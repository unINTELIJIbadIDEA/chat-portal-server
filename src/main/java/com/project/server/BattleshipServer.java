package com.project.server;

import com.project.models.battleship.*;
import com.project.models.battleship.messages.*;
import com.project.services.BattleshipGameService;
import com.project.utils.Config;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BattleshipServer {
    private static final BattleshipServer INSTANCE = new BattleshipServer();

    private final ExecutorService executor;
    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private final BattleshipGameService gameService = new BattleshipGameService();

    // Gry, ktore są aktywne
    private final Map<String, BattleshipGame> activeGames = new ConcurrentHashMap<>();
    // Połączenia graczy w grach
    private final Map<String, Map<Integer, BattleshipClientHandler>> gameConnections = new ConcurrentHashMap<>();

    private BattleshipServer() {
        executor = Executors.newCachedThreadPool();
    }

    public static BattleshipServer getInstance() {
        return INSTANCE;
    }

    public void runServer() {
        try {
            serverSocket = new ServerSocket(Config.getBATTLESHIP_SERVER_PORT());
            System.out.println("[BATTLESHIP SERVER]: Server started on port " + Config.getBATTLESHIP_SERVER_PORT());

            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    System.out.println("[BATTLESHIP SERVER]: New connection from " + socket.getRemoteSocketAddress());
                    executor.submit(new BattleshipClientHandler(socket, this));
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[BATTLESHIP SERVER]: Error accepting connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[BATTLESHIP SERVER]: Failed to start: " + e.getMessage());
        } finally {
            stopServer();
        }
    }

    public void stopServer() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                System.out.println("[BATTLESHIP SERVER]: Socket closed.");
            } catch (IOException e) {
                System.err.println("[BATTLESHIP SERVER]: Error closing socket: " + e.getMessage());
            }
        }
        executor.shutdownNow();
        System.out.println("[BATTLESHIP SERVER]: Executor shutdown.");
    }

    public void registerGame(String gameId) {
        activeGames.put(gameId, new BattleshipGame(gameId));
        gameConnections.put(gameId, new ConcurrentHashMap<>());
        System.out.println("[BATTLESHIP SERVER]: Registered new game: " + gameId);
    }

    public void addPlayerToGame(String gameId, int playerId) {
        BattleshipGame game = activeGames.get(gameId);
        if (game != null) {
            game.addPlayer(playerId);
            System.out.println("[BATTLESHIP SERVER]: Player " + playerId + " added to game " + gameId);
        }
    }

    public void handlePlayerConnection(String gameId, int playerId, BattleshipClientHandler handler) {
        Map<Integer, BattleshipClientHandler> connections = gameConnections.get(gameId);
        if (connections != null) {
            connections.put(playerId, handler);
            System.out.println("[BATTLESHIP SERVER]: Player " + playerId + " connected to game " + gameId);

            // Wyślij aktualny stan gry
            BattleshipGame game = activeGames.get(gameId);
            if (game != null) {
                handler.sendMessage(new GameUpdateMessage(game));
            }
        }
    }

    public void handleBattleshipMessage(String gameId, BattleshipMessage message) {
        BattleshipGame game = activeGames.get(gameId);
        if (game == null) {
            System.err.println("[BATTLESHIP SERVER]: Game not found: " + gameId);
            return;
        }

        switch (message.getType()) {
            case PLACE_SHIP:
                handlePlaceShip(gameId, game, (PlaceShipMessage) message);
                break;
            case TAKE_SHOT:
                handleTakeShot(gameId, game, (TakeShotMessage) message);
                break;
            default:
                System.err.println("[BATTLESHIP SERVER]: Unknown message type: " + message.getType());
                break;
        }
    }

    private void handlePlaceShip(String gameId, BattleshipGame game, PlaceShipMessage message) {
        boolean placed = game.placeShip(
                message.getPlayerId(),
                message.getShipType(),
                message.getX(),
                message.getY(),
                message.isHorizontal()
        );

        System.out.println("[BATTLESHIP SERVER]: Ship placement " + (placed ? "successful" : "failed") +
                " for player " + message.getPlayerId() + " in game " + gameId);

        // Broadcast update do wszystkich graczy w tej grze
        if (placed) {
            broadcastToGame(gameId, new GameUpdateMessage(game));

            // czy gra powinna się rozpocząć
            if (game.getState() == GameState.PLAYING) {
                try {
                    gameService.updateGameStatus(gameId, "PLAYING", null);
                } catch (Exception e) {
                    System.err.println("[BATTLESHIP SERVER]: Failed to update game status: " + e.getMessage());
                }
            }
        }
    }

    private void handleTakeShot(String gameId, BattleshipGame game, TakeShotMessage message) {
        ShotResult result = game.takeShot(message.getPlayerId(), message.getX(), message.getY());

        System.out.println("[BATTLESHIP SERVER]: Shot result " + result + " for player " + message.getPlayerId() +
                " at (" + message.getX() + "," + message.getY() + ") in game " + gameId);

        // Wyślij wynik strzału
        broadcastToGame(gameId, new ShotResultMessage(message.getPlayerId(), gameId, result, message.getX(), message.getY()));

        // Wyślij zaktualizowany stan gry
        broadcastToGame(gameId, new GameUpdateMessage(game));

        // Jeśli gra się skończyła, zaktualizuje status w bazie
        if (result == ShotResult.GAME_OVER) {
            try {
                gameService.updateGameStatus(gameId, "FINISHED", message.getPlayerId());
            } catch (Exception e) {
                System.err.println("[BATTLESHIP SERVER]: Failed to update game status: " + e.getMessage());
            }
        }
    }

    private void broadcastToGame(String gameId, BattleshipMessage message) {
        Map<Integer, BattleshipClientHandler> connections = gameConnections.get(gameId);
        if (connections != null) {
            connections.values().forEach(handler -> handler.sendMessage(message));
        }
    }

    public void removePlayerFromGame(String gameId, int playerId) {
        Map<Integer, BattleshipClientHandler> connections = gameConnections.get(gameId);
        if (connections != null) {
            connections.remove(playerId);
            if (connections.isEmpty()) {
                activeGames.remove(gameId);
                gameConnections.remove(gameId);
                System.out.println("[BATTLESHIP SERVER]: Game " + gameId + " removed - no players left");
            }
        }
    }

    public BattleshipGame getGame(String gameId) {
        return activeGames.get(gameId);
    }
}