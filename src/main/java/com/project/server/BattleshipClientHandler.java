package com.project.server;

import com.project.models.battleship.messages.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class BattleshipClientHandler implements Runnable {
    private final Socket socket;
    private final BattleshipServer server;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private String currentGameId;
    private int playerId;
    private volatile boolean running = true;

    public BattleshipClientHandler(Socket socket, BattleshipServer server) throws IOException {
        this.socket = socket;
        this.server = server;

        try {
            System.out.println("[BATTLESHIP CLIENT HANDLER]: Initializing streams...");

            // Najpierw OutputStream z flush
            this.out = new ObjectOutputStream(socket.getOutputStream());
            this.out.flush();

            // Krótkie opóźnienie
            Thread.sleep(100);

            // Potem InputStream
            this.in = new ObjectInputStream(socket.getInputStream());

            System.out.println("[BATTLESHIP CLIENT HANDLER]: Streams initialized successfully");

        } catch (Exception e) {
            System.err.println("[BATTLESHIP CLIENT HANDLER]: Stream initialization failed: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Failed to initialize streams", e);
        }
    }

    @Override
    public void run() {
        try {
            while (running && !socket.isClosed() && !socket.isInputShutdown()) {
                try {
                    System.out.println("[BATTLESHIP CLIENT HANDLER]: Waiting for message from player " + playerId + "...");
                    BattleshipMessage message = (BattleshipMessage) in.readObject();
                    System.out.println("[BATTLESHIP CLIENT HANDLER]: Received " + message.getType() + " from player " + playerId);
                    handleMessage(message);
                } catch (SocketTimeoutException e) {
                    // Timeout jest ok - kontynuuj
                    continue;
                } catch (ClassNotFoundException e) {
                    System.err.println("[BATTLESHIP CLIENT HANDLER]: Invalid message class: " + e.getMessage());
                    break;
                } catch (EOFException e) {
                    System.out.println("[BATTLESHIP CLIENT HANDLER]: Connection closed by client " + playerId);
                    break;
                } catch (IOException e) {
                    System.err.println("[BATTLESHIP CLIENT HANDLER]: IO Error for player " + playerId + ": " + e.getMessage());
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[BATTLESHIP CLIENT HANDLER]: Unexpected error for player " + playerId + ": " + e.getMessage());
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
                System.out.println("[BATTLESHIP CLIENT HANDLER]: Processing JOIN_GAME for player " + playerId + " in game " + currentGameId);
                server.handlePlayerConnection(currentGameId, playerId, this);
                break;

            default:
                if (currentGameId != null) {
                    System.out.println("[BATTLESHIP CLIENT HANDLER]: Forwarding " + message.getType() + " from player " + playerId + " to server");
                    server.handleBattleshipMessage(currentGameId, message);
                } else {
                    System.err.println("[BATTLESHIP CLIENT HANDLER]: Message received before joining game: " + message.getType());
                }
                break;
        }
    }

    public synchronized void sendMessage(BattleshipMessage message) {
        try {
            if (out != null && running) {
                System.out.println("[BATTLESHIP CLIENT HANDLER]: Sending " + message.getType() + " to player " + playerId);
                out.writeObject(message);
                out.flush();
                out.reset(); // WAŻNE: Reset strumienia obiektów
                System.out.println("[BATTLESHIP CLIENT HANDLER]: Message sent successfully to player " + playerId);
            } else {
                System.err.println("[BATTLESHIP CLIENT HANDLER]: Cannot send message - handler not running or output stream null");
            }
        } catch (IOException e) {
            System.err.println("[BATTLESHIP CLIENT HANDLER]: Error sending message to player " + playerId + ": " + e.getMessage());
            e.printStackTrace();
            running = false;
        }
    }

    public boolean isConnected() {
        return running &&
                socket != null &&
                !socket.isClosed() &&
                !socket.isInputShutdown() &&
                !socket.isOutputShutdown();
    }

    private void cleanup() {
        running = false;
        if (currentGameId != null && playerId != 0) {
            System.out.println("[BATTLESHIP CLIENT HANDLER]: Cleaning up player " + playerId + " from game " + currentGameId);
            server.removePlayerFromGame(currentGameId, playerId);
        }
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[BATTLESHIP CLIENT HANDLER]: Error during cleanup: " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return running && socket != null && !socket.isClosed();
    }

}