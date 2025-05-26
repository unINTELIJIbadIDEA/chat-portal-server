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

    public BattleshipClientHandler(Socket socket, BattleshipServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    @Override
    public void run() {
        try {
            while (!socket.isClosed() && !socket.isInputShutdown()) {
                try {
                    BattleshipMessage message = (BattleshipMessage) in.readObject();
                    handleMessage(message);
                } catch (ClassNotFoundException e) {
                    System.err.println("[BATTLESHIP CLIENT]: Invalid message class: " + e.getMessage());
                    break;
                } catch (EOFException e) {
                    System.out.println("[BATTLESHIP CLIENT]: Connection closed by client");
                    break;
                }
            }
        } catch (IOException e) {
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
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            System.err.println("[BATTLESHIP CLIENT]: Error sending message: " + e.getMessage());
        }
    }

    private void cleanup() {
        if (currentGameId != null && playerId != 0) {
            server.removePlayerFromGame(currentGameId, playerId);
        }
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            socket.close();
        } catch (IOException e) {
            System.err.println("[BATTLESHIP CLIENT]: Error closing socket: " + e.getMessage());
        }
    }
}
