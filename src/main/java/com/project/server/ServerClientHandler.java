package com.project.server;

import com.project.models.Message;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;

public class ServerClientHandler implements Callable<Void> {

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

    private void handleJoin(Message message) throws IOException {
        String newRoomId = message.content().substring(6);
        ServerSessionManager sessionManager = ServerSessionManager.getInstance();

        if (!sessionManager.sessionExists(newRoomId)) {
            sessionManager.createSession(newRoomId);
        }

        sessionManager.addClientToSession(newRoomId, this);
        this.roomId = newRoomId;
        sendMessage(new Message(0, newRoomId, 0, "Joined room: " + newRoomId, LocalDateTime.now()));
    }

    @Override
    public Void call() {
        try {
            while (!socket.isClosed()) {
                Message message = (Message) in.readObject();

                if (roomId == null && message.content().startsWith("/join ")) {
                    handleJoin(message);
                } else {
                    ServerSessionManager.getInstance()
                            .getRoom(roomId)
                            .broadcast(message);
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


}
