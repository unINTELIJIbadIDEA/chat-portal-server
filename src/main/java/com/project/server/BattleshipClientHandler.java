package com.project.server;

import com.project.models.battleship.messages.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.logging.*;

public class BattleshipClientHandler implements Runnable {
    private static final Logger logger = Logger.getLogger(BattleshipServer.class.getName());

    static {
        try {
            FileHandler fileHandler = new FileHandler("logs/battleshipclienthandler.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.INFO);
        } catch (IOException e) {
            System.err.println("Logger initialization failed for BattleshipServer: " + e.getMessage());
        }
    }
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
            logger.info("[BATTLESHIP CLIENT HANDLER]: Initializing streams...");

            this.out = new ObjectOutputStream(socket.getOutputStream());
            this.out.flush();

            Thread.sleep(100);

            this.in = new ObjectInputStream(socket.getInputStream());

            logger.info("[BATTLESHIP CLIENT HANDLER]: Streams initialized successfully");

        } catch (Exception e) {
            logger.warning("[BATTLESHIP CLIENT HANDLER]: Stream initialization failed: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Failed to initialize streams", e);
        }
    }

    @Override
    public void run() {
        logger.info("[BATTLESHIP CLIENT HANDLER]: Handler started for player " + playerId);

        try {
            while (running && !socket.isClosed() && !socket.isInputShutdown()) {
                try {

                    if (!socket.isConnected()) {
                        logger.info("[BATTLESHIP CLIENT HANDLER]: Socket disconnected for player " + playerId);
                        break;
                    }

                    BattleshipMessage message = (BattleshipMessage) in.readObject();
                    logger.info("[BATTLESHIP CLIENT HANDLER]: Received " + message.getType() + " from player " + playerId);
                    handleMessage(message);

                } catch (SocketTimeoutException e) {

                    if (!socket.isConnected() || socket.isClosed()) {
                        logger.info("[BATTLESHIP CLIENT HANDLER]: Socket closed during timeout for player " + playerId);
                        break;
                    }
                    continue;
                } catch (EOFException e) {
                    logger.info("[BATTLESHIP CLIENT HANDLER]: Connection closed by client " + playerId);
                    break;
                } catch (java.net.SocketException e) {
                    logger.info("[BATTLESHIP CLIENT HANDLER]: Socket exception for player " + playerId + ": " + e.getMessage());
                    break;
                } catch (IOException e) {
                    logger.severe("[BATTLESHIP CLIENT HANDLER]: IO Error for player " + playerId + ": " + e.getMessage());
                    break;
                } catch (ClassNotFoundException e) {
                    logger.warning("[BATTLESHIP CLIENT HANDLER]: Invalid message class: " + e.getMessage());
                    break;
                }
            }
        } catch (Exception e) {
            logger.severe("[BATTLESHIP CLIENT HANDLER]: Unexpected error for player " + playerId + ": " + e.getMessage());
        } finally {
            logger.info("[BATTLESHIP CLIENT HANDLER]: Handler ending for player " + playerId);
            cleanup();
        }
    }


    private void handleMessage(BattleshipMessage message) {
        switch (message.getType()) {
            case JOIN_GAME:
                JoinGameMessage joinMsg = (JoinGameMessage) message;
                this.currentGameId = joinMsg.getGameId();
                this.playerId = joinMsg.getPlayerId();
                logger.info("[BATTLESHIP CLIENT HANDLER]: Processing JOIN_GAME for player " + playerId + " in game " + currentGameId);
                server.handlePlayerConnection(currentGameId, playerId, this);
                break;

            default:
                if (currentGameId != null) {
                    logger.info("[BATTLESHIP CLIENT HANDLER]: Forwarding " + message.getType() + " from player " + playerId + " to server");
                    server.handleBattleshipMessage(currentGameId, message);
                } else {
                    logger.warning("[BATTLESHIP CLIENT HANDLER]: Message received before joining game: " + message.getType());
                }
                break;
        }
    }

    public synchronized void sendMessage(BattleshipMessage message) {
        try {
            if (out != null && running) {
                logger.info("[BATTLESHIP CLIENT HANDLER]: Sending " + message.getType() + " to player " + playerId);
                out.writeObject(message);
                out.flush();
                out.reset();
                logger.info("[BATTLESHIP CLIENT HANDLER]: Message sent successfully to player " + playerId);
            } else {
                logger.warning("[BATTLESHIP CLIENT HANDLER]: Cannot send message - handler not running or output stream null");
            }
        } catch (IOException e) {
            logger.severe("[BATTLESHIP CLIENT HANDLER]: Error sending message to player " + playerId + ": " + e.getMessage());
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

        logger.info("[BATTLESHIP CLIENT HANDLER]: === CLEANUP START ===");
        logger.info("[BATTLESHIP CLIENT HANDLER]: Player " + playerId + " disconnecting from game " + currentGameId);

        if (currentGameId != null && playerId != 0) {
            logger.info("[BATTLESHIP CLIENT HANDLER]: Notifying server about player disconnection");
            server.removePlayerFromGame(currentGameId, playerId);
        }

        try {
            if (in != null) {
                in.close();
                logger.info("[BATTLESHIP CLIENT HANDLER]: Input stream closed");
            }
        } catch (IOException e) {
            logger.severe("[BATTLESHIP CLIENT HANDLER]: Error closing input stream: " + e.getMessage());
        }

        try {
            if (out != null) {
                out.close();
                logger.info("[BATTLESHIP CLIENT HANDLER]: Output stream closed");
            }
        } catch (IOException e) {
            logger.severe("[BATTLESHIP CLIENT HANDLER]: Error closing output stream: " + e.getMessage());
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                logger.info("[BATTLESHIP CLIENT HANDLER]: Socket closed");
            }
        } catch (IOException e) {
            logger.severe("[BATTLESHIP CLIENT HANDLER]: Error closing socket: " + e.getMessage());
        }

        logger.info("[BATTLESHIP CLIENT HANDLER]: === CLEANUP COMPLETE ===");
    }

    public boolean isRunning() {
        return running && socket != null && !socket.isClosed();
    }

}