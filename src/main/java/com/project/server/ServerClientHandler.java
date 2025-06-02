package com.project.server;

import com.project.models.message.ClientMessage;
import com.project.models.message.Message;
import com.project.models.message.MessageRequest;
import com.project.config.ConfigProperties;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;

public class ServerClientHandler implements Callable<Void> {

    private static final Logger logger = Logger.getLogger(ServerClientHandler.class.getName());

    static {
        try {
            FileHandler fileHandler = new FileHandler("logs/clienthandler.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.INFO);
        } catch (IOException e) {
            System.err.println("Logger init failed: " + e.getMessage());
        }
    }

    private static final AtomicInteger messageId = new AtomicInteger(0);

    private final Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private String roomId;

    public ServerClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    public void sendMessage(Message message) throws IOException {
        out.writeObject(message);
        out.flush();
    }

    private void handleJoin(ClientMessage clientMessage) throws IOException {
        String roomId = clientMessage.chatId();
        String token = clientMessage.token();

        Integer userId = ApiServer.getTokenManager().validateTokenAndGetUserId(token);
        if (userId == null) {
            logger.warning("Invalid token during join attempt from: " + socket.getRemoteSocketAddress());
            sendMessage(new Message(0, roomId, 0, "Invalid or expired token", LocalDateTime.now()));
            return;
        }

        ServerSessionManager sessionManager = ServerSessionManager.getInstance();
        if (!sessionManager.sessionExists(roomId)) {
            sessionManager.createSession(roomId);
            logger.info("New room created: " + roomId);
        }

        sessionManager.addClientToSession(roomId, this);
        this.roomId = roomId;

        logger.info("Client joined room: " + roomId + " (userId: " + userId + ")");
        sendMessage(new Message(0, roomId, userId, "Joined room: " + roomId, LocalDateTime.now()));
    }

    // NOWA METODA - obsługa powiadomień o grach
    private void handleGameNotification(String gameData, String chatId, int userId) {
        try {
            // Wyślij specjalną wiadomość o grze do wszystkich w czacie
            Message gameMessage = new Message(
                    messageId.incrementAndGet(),
                    chatId,
                    userId,
                    "[BATTLESHIP_GAME]" + gameData,
                    LocalDateTime.now()
            );

            // Wyślij do wszystkich w sesji czatu
            if (ServerSessionManager.getInstance().sessionExists(chatId)) {
                ServerSessionManager.getInstance()
                        .getRoom(chatId)
                        .broadcast(gameMessage);

                // Zapisz wiadomość w bazie danych przez API
                sendMessageToApi(ApiServer.getTokenManager().generateToken(String.valueOf(userId)), gameMessage);
            }

        } catch (Exception e) {
            System.err.println("Error handling game notification: " + e.getMessage());
        }
    }

    @Override
    public Void call() {
        try {
            while (!socket.isClosed()) {
                ClientMessage clientMessage = (ClientMessage) in.readObject();

                if (clientMessage.content().startsWith("/join")) {
                    handleJoin(clientMessage);
                    continue;
                }

                // NOWA LOGIKA - sprawdź czy to powiadomienie o grze
                if (clientMessage.content().startsWith("/game_notification:")) {
                    Integer userId = ApiServer.getTokenManager().validateTokenAndGetUserId(clientMessage.token());
                    if (userId != null) {
                        String gameData = clientMessage.content().substring("/game_notification:".length());
                        handleGameNotification(gameData, clientMessage.chatId(), userId);
                    }
                    continue;
                }

                Integer userId = ApiServer.getTokenManager().validateTokenAndGetUserId(clientMessage.token());
                if (userId == null) {
                    logger.warning("Invalid token from: " + socket.getRemoteSocketAddress());
                    sendMessage(new Message(0, clientMessage.chatId(), 0, "Invalid or expired token", LocalDateTime.now()));
                    continue;
                }

                Message messageToSend = new Message(
                        messageId.incrementAndGet(),
                        clientMessage.chatId(),
                        userId,
                        clientMessage.content(),
                        LocalDateTime.now()
                );

                if (ServerSessionManager.getInstance().sessionExists(clientMessage.chatId())) {
                    ServerSessionManager.getInstance()
                            .getRoom(clientMessage.chatId())
                            .broadcast(messageToSend);

                    logger.info("Broadcasted message in room " + clientMessage.chatId() + " from user " + userId);
                    sendMessageToApi(clientMessage.token(), messageToSend);
                } else {
                    logger.warning("Attempt to message non-existing room: " + clientMessage.chatId());
                    sendMessage(new Message(0, clientMessage.chatId(), userId, "Room does not exist. Please join first.", LocalDateTime.now()));
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            logger.warning("Exception in handler: " + e.getMessage());
        } finally {
            if (roomId != null) {
                ServerSessionManager.getInstance().removeClientFromSession(roomId, this);
                logger.info("Client disconnected from room: " + roomId);
            }
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("[SYSTEM]: " + e.getMessage());
            }
        }
        return null;
    }

    public void update(Message message) {
        try {
            sendMessage(message);
        } catch (IOException e) {
            ServerSessionManager.getInstance().removeClientFromSession(roomId, this);
            logger.warning("Failed to update client in room: " + roomId);
        }
    }

    private void sendMessageToApi(String token, Message message) {
        try {
            URL url = new java.net.URL("http://" + ConfigProperties.getHOST_SERVER() + ":" + ConfigProperties.getLOCAL_API_PORT() + "/api/messages");
            HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setDoOutput(true);

            String json = ApiServer.gson.toJson(new MessageRequest(message.chat_id(), message.content()));

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 201) {
                System.out.println("[API ERROR] Failed to POST message, response code: " + responseCode);
            }
        } catch (IOException e) {
            System.out.println("[API ERROR] " + e.getMessage());
        }
    }
}