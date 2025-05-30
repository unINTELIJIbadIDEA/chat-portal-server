package com.project.server;

import com.project.models.battleship.messages.*;

import java.io.*;
import java.net.Socket;

public class BattleshipClientHandler implements Runnable {
    private final Socket socket;
    private final BattleshipServer server;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private String currentGameId;
    private int playerId;
    private volatile boolean isRunning = true;

    public BattleshipClientHandler(Socket socket, BattleshipServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.isRunning = true; // Ustaw na początku

        try {
            System.out.println("[BATTLESHIP CLIENT]: Initializing streams...");
            this.out = new ObjectOutputStream(socket.getOutputStream());
            this.out.flush();
            Thread.sleep(100);
            this.in = new ObjectInputStream(socket.getInputStream());
            System.out.println("[BATTLESHIP CLIENT]: Streams initialized successfully");
        } catch (Exception e) {
            System.err.println("[BATTLESHIP CLIENT]: Stream initialization failed: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Failed to initialize streams", e);
        }
    }

    @Override
    public void run() {
        try {
            while (isRunning && !socket.isClosed() && !socket.isInputShutdown()) {
                try {
                    System.out.println("[BATTLESHIP CLIENT]: Waiting for message...");
                    BattleshipMessage message = (BattleshipMessage) in.readObject();
                    System.out.println("[BATTLESHIP CLIENT]: Received message: " + message.getType());
                    handleMessage(message);
                } catch (ClassNotFoundException e) {
                    System.err.println("[BATTLESHIP CLIENT]: Invalid message class: " + e.getMessage());
                    break;
                } catch (EOFException e) {
                    System.out.println("[BATTLESHIP CLIENT]: Connection closed by client");
                    break;
                } catch (StreamCorruptedException e) {
                    System.err.println("[BATTLESHIP CLIENT]: Stream corrupted: " + e.getMessage());
                    break;
                } catch (IOException e) {
                    System.err.println("[BATTLESHIP CLIENT]: IO Error: " + e.getMessage());
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("[BATTLESHIP CLIENT]: Connection error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleMessage(BattleshipMessage message) {
        switch (message.getType()) {
            case JOIN_GAME:
                JoinGameMessage joinMsg = (JoinGameMessage) message;
                this.currentGameId = joinMsg.getGameId();
                this.playerId = joinMsg.getPlayerId();
                server.handlePlayerConnection(currentGameId, playerId, this);
                System.out.println("[BATTLESHIP CLIENT]: Player " + playerId + " joined game " + currentGameId);
                break;
            default:
                if (currentGameId != null) {
                    server.handleBattleshipMessage(currentGameId, message);
                } else {
                    System.err.println("[BATTLESHIP CLIENT]: Message received before joining game: " + message.getType());
                }
                break;
        }
    }

    public void sendMessage(BattleshipMessage message) {
        try {
            if (out != null && isConnected()) {
                System.out.println("[BATTLESHIP CLIENT]: Sending message: " + message.getType());
                out.writeObject(message);
                out.flush();
                System.out.println("[BATTLESHIP CLIENT]: Message sent successfully");
            } else {
                System.err.println("[BATTLESHIP CLIENT]: Cannot send message - not connected");
            }
        } catch (IOException e) {
            System.err.println("[BATTLESHIP CLIENT]: Error sending message: " + e.getMessage());
            cleanup();
        }
    }

    public boolean isConnected() {
        return isRunning &&
                socket != null &&
                !socket.isClosed() &&
                !socket.isInputShutdown() &&
                !socket.isOutputShutdown();
    }

    public void cleanup() {
        isRunning = false;

        if (currentGameId != null && playerId != 0) {
            server.removePlayerFromGame(currentGameId, playerId);
        }

        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[BATTLESHIP CLIENT]: Error closing socket: " + e.getMessage());
        }

        System.out.println("[BATTLESHIP CLIENT]: Cleanup completed for player: " + playerId);
    }

}