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
        System.out.println("[BATTLESHIP SERVER]: Registering game: " + gameId);

        // Sprawdź czy gra już istnieje
        if (activeGames.containsKey(gameId)) {
            System.out.println("[BATTLESHIP SERVER]: Game " + gameId + " already registered");
            return;
        }

        // Utwórz nową grę
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
        System.out.println("[BATTLESHIP SERVER]: === PLAYER CONNECTION ===");
        System.out.println("[BATTLESHIP SERVER]: Game ID: " + gameId);
        System.out.println("[BATTLESHIP SERVER]: Player ID: " + playerId);

        Map<Integer, BattleshipClientHandler> connections = gameConnections.get(gameId);
        if (connections == null) {
            System.err.println("[BATTLESHIP SERVER]: No connections map for game " + gameId + " - creating new one");
            connections = new ConcurrentHashMap<>();
            gameConnections.put(gameId, connections);
        }

        // Dodaj połączenie gracza
        connections.put(playerId, handler);
        System.out.println("[BATTLESHIP SERVER]: Player " + playerId + " connected to game " + gameId);
        System.out.println("[BATTLESHIP SERVER]: Total connections for game: " + connections.size());

        // Pobierz grę
        BattleshipGame game = activeGames.get(gameId);
        if (game == null) {
            System.err.println("[BATTLESHIP SERVER]: Game " + gameId + " not found - creating new one");
            game = new BattleshipGame(gameId);
            activeGames.put(gameId, game);
        }

        // Dodaj gracza do gry
        boolean added = game.addPlayer(playerId);
        System.out.println("[BATTLESHIP SERVER]: Player " + playerId +
                (added ? " successfully added" : " already in game or game full") + " to game " + gameId);

        System.out.println("[BATTLESHIP SERVER]: Current game state: " + game.getState());
        System.out.println("[BATTLESHIP SERVER]: Players in game: " + game.getPlayerBoards().keySet());
        System.out.println("[BATTLESHIP SERVER]: Players ready: " + game.getPlayersReady());

        // KRYTYCZNE: Zawsze broadcast do WSZYSTKICH graczy
        GameUpdateMessage updateMessage = new GameUpdateMessage(game);
        System.out.println("[BATTLESHIP SERVER]: Broadcasting game update to " + connections.size() + " players");

        // Wyślij do każdego gracza osobno z logowaniem
        for (Map.Entry<Integer, BattleshipClientHandler> entry : connections.entrySet()) {
            try {
                System.out.println("[BATTLESHIP SERVER]: Sending update to player " + entry.getKey());
                entry.getValue().sendMessage(updateMessage);
                System.out.println("[BATTLESHIP SERVER]: Update sent successfully to player " + entry.getKey());
            } catch (Exception e) {
                System.err.println("[BATTLESHIP SERVER]: Failed to send update to player " + entry.getKey() + ": " + e.getMessage());
            }
        }

        // DODATKOWE: Jeśli mamy dwóch graczy, zaktualizuj status w bazie
        if (game.getPlayerBoards().size() == 2 && game.getState() == GameState.SHIP_PLACEMENT) {
            try {
                // Aktualizuj status w bazie na SHIP_PLACEMENT
                System.out.println("[BATTLESHIP SERVER]: Updating database status to SHIP_PLACEMENT");
                // Tu możesz dodać wywołanie do gameService jeśli potrzebne
            } catch (Exception e) {
                System.err.println("[BATTLESHIP SERVER]: Failed to update database status: " + e.getMessage());
            }
        }

        System.out.println("[BATTLESHIP SERVER]: === CONNECTION HANDLING COMPLETE ===");
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
        System.out.println("[BATTLESHIP SERVER]: === PLACE SHIP ===");
        System.out.println("[BATTLESHIP SERVER]: Player " + message.getPlayerId() + " placing " +
                message.getShipType() + " at (" + message.getX() + "," + message.getY() + ")");

        boolean placed = game.placeShip(
                message.getPlayerId(),
                message.getShipType(),
                message.getX(),
                message.getY(),
                message.isHorizontal()
        );

        System.out.println("[BATTLESHIP SERVER]: Ship placement " + (placed ? "successful" : "failed"));

        if (placed) {
            // Sprawdź stan gry
            System.out.println("[BATTLESHIP SERVER]: Current game state: " + game.getState());
            System.out.println("[BATTLESHIP SERVER]: Players ready: " + game.getPlayersReady());

            // Sprawdź czy wszyscy gracze są gotowi
            boolean allReady = game.getPlayersReady().values().stream().allMatch(ready -> ready);
            System.out.println("[BATTLESHIP SERVER]: All players ready: " + allReady);

            // Broadcast update do wszystkich graczy w tej grze
            broadcastToGame(gameId, new GameUpdateMessage(game));

            // Jeśli gra przeszła do stanu PLAYING, zaktualizuj bazę danych
            if (game.getState() == GameState.PLAYING) {
                System.out.println("[BATTLESHIP SERVER]: Game is now in PLAYING state!");
                try {
                    gameService.updateGameStatus(gameId, "PLAYING", null);
                    System.out.println("[BATTLESHIP SERVER]: Database updated to PLAYING status");
                } catch (Exception e) {
                    System.err.println("[BATTLESHIP SERVER]: Failed to update game status in database: " + e.getMessage());
                }
            }
        } else {
            System.err.println("[BATTLESHIP SERVER]: Failed to place ship for player " + message.getPlayerId());
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