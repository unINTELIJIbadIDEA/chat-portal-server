package com.project.server;

import com.project.models.message.ClientMessage;
import com.project.models.message.Message;
import com.project.models.message.MessageRequest;
import com.project.utils.Config;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerClientHandler implements Callable<Void> {

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

        System.out.println("=== JOIN REQUEST DEBUG ===");
        System.out.println("Room ID: " + roomId);
        System.out.println("Token: " + (token != null ? token.substring(0, Math.min(10, token.length())) + "..." : "null"));
        System.out.println("Client address: " + socket.getRemoteSocketAddress());

        Integer userId = ApiServer.getTokenManager().validateTokenAndGetUserId(token);
        if (userId == null) {
            System.err.println("INVALID TOKEN for room: " + roomId);
            sendMessage(new Message(0, roomId, 0, "Invalid or expired token", LocalDateTime.now()));
            return;
        }

        System.out.println("User ID from token: " + userId);

        ServerSessionManager sessionManager = ServerSessionManager.getInstance();
        if (!sessionManager.sessionExists(roomId)) {
            System.out.println("Creating new session for room: " + roomId);
            sessionManager.createSession(roomId);
        } else {
            System.out.println("Session already exists for room: " + roomId);
        }

        sessionManager.addClientToSession(roomId, this);
        this.roomId = roomId;

        System.out.println("Successfully added client to room: " + roomId);
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

                    sendMessageToApi(clientMessage.token(), messageToSend);
                } else {
                    sendMessage(new Message(0, clientMessage.chatId(), userId, "Room does not exist. Please join first.", LocalDateTime.now()));
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("[SYSTEM]: " + e.getMessage());
        } finally {
            if (roomId != null) {
                ServerSessionManager.getInstance().removeClientFromSession(roomId, this);
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
        }
    }

    private void sendMessageToApi(String token, Message message) {
        try {
            URL url = new java.net.URL("http://" + Config.getHOST_SERVER() + ":" + Config.getLOCAL_API_PORT() + "/api/messages");
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