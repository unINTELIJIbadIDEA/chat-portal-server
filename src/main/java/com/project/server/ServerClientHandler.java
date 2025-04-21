package com.project.server;

import com.project.models.message.ClientMessage;
import com.project.models.message.Message;
import com.project.models.message.MessageRequest;
import com.project.utils.Config;

import java.io.*;
import java.net.Socket;
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

        Integer userId = ApiServer.getTokenManager().validateTokenAndGetUserId(token);
        if (userId == null) {
            sendMessage(new Message(0, roomId, 0, "Invalid or expired token", LocalDateTime.now()));
            return;
        }

        ServerSessionManager sessionManager = ServerSessionManager.getInstance();
        if (!sessionManager.sessionExists(roomId)) {
            sessionManager.createSession(roomId);
        }

        sessionManager.addClientToSession(roomId, this);
        this.roomId = roomId;

        sendMessage(new Message(0, roomId, userId, "Joined room: " + roomId, LocalDateTime.now()));
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
            java.net.URL url = new java.net.URL("http://localhost:" + Config.getPORT_API() + "/api/messages");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
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