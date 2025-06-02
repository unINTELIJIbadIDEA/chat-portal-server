package com.project.server;

import com.project.models.battleship.messages.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        System.out.println("[BATTLESHIP CLIENT HANDLER]: Handler started for player " + playerId);

        try {
            while (running && !socket.isClosed() && !socket.isInputShutdown()) {
                try {
                    // DODAJ SPRAWDZENIE CONNECTION STATUS
                    if (!socket.isConnected()) {
                        System.out.println("[BATTLESHIP CLIENT HANDLER]: Socket disconnected for player " + playerId);
                        break;
                    }

                    BattleshipMessage message = (BattleshipMessage) in.readObject();
                    System.out.println("[BATTLESHIP CLIENT HANDLER]: Received " + message.getType() + " from player " + playerId);
                    handleMessage(message);

                } catch (SocketTimeoutException e) {
                    // Sprawdź czy socket wciąż działa
                    if (!socket.isConnected() || socket.isClosed()) {
                        System.out.println("[BATTLESHIP CLIENT HANDLER]: Socket closed during timeout for player " + playerId);
                        break;
                    }
                    continue;
                } catch (EOFException e) {
                    System.out.println("[BATTLESHIP CLIENT HANDLER]: Connection closed by client " + playerId);
                    break;
                } catch (java.net.SocketException e) {
                    System.out.println("[BATTLESHIP CLIENT HANDLER]: Socket exception for player " + playerId + ": " + e.getMessage());
                    break;
                } catch (IOException e) {
                    System.err.println("[BATTLESHIP CLIENT HANDLER]: IO Error for player " + playerId + ": " + e.getMessage());
                    break;
                } catch (ClassNotFoundException e) {
                    System.err.println("[BATTLESHIP CLIENT HANDLER]: Invalid message class: " + e.getMessage());
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[BATTLESHIP CLIENT HANDLER]: Unexpected error for player " + playerId + ": " + e.getMessage());
        } finally {
            System.out.println("[BATTLESHIP CLIENT HANDLER]: Handler ending for player " + playerId);
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

        System.out.println("[BATTLESHIP CLIENT HANDLER]: === CLEANUP START ===");
        System.out.println("[BATTLESHIP CLIENT HANDLER]: Player " + playerId + " disconnecting from game " + currentGameId);

        // KRYTYCZNE: Powiadom serwer o rozłączeniu PRZED zamknięciem połączeń
        if (currentGameId != null && playerId != 0) {
            System.out.println("[BATTLESHIP CLIENT HANDLER]: Notifying server about player disconnection");
            server.removePlayerFromGame(currentGameId, playerId);
        }

        try {
            if (in != null) {
                in.close();
                System.out.println("[BATTLESHIP CLIENT HANDLER]: Input stream closed");
            }
        } catch (IOException e) {
            System.err.println("[BATTLESHIP CLIENT HANDLER]: Error closing input stream: " + e.getMessage());
        }

        try {
            if (out != null) {
                out.close();
                System.out.println("[BATTLESHIP CLIENT HANDLER]: Output stream closed");
            }
        } catch (IOException e) {
            System.err.println("[BATTLESHIP CLIENT HANDLER]: Error closing output stream: " + e.getMessage());
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("[BATTLESHIP CLIENT HANDLER]: Socket closed");
            }
        } catch (IOException e) {
            System.err.println("[BATTLESHIP CLIENT HANDLER]: Error closing socket: " + e.getMessage());
        }

        System.out.println("[BATTLESHIP CLIENT HANDLER]: === CLEANUP COMPLETE ===");
    }

    public boolean isRunning() {
        return running && socket != null && !socket.isClosed();
    }

}