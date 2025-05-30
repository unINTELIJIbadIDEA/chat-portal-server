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
    private java.util.Timer connectionMonitor;

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

            // DODAJ: Uruchom monitor połączeń
            startConnectionMonitor();

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

    private void startConnectionMonitor() {
        connectionMonitor = new java.util.Timer("ConnectionMonitor", true);

        // Sprawdzaj połączenia co 10 sekund
        connectionMonitor.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                try {
                    System.out.println("[BATTLESHIP SERVER]: Running connection check...");
                    checkConnections();
                    cleanupEmptyGames();
                } catch (Exception e) {
                    System.err.println("[BATTLESHIP SERVER]: Error during connection check: " + e.getMessage());
                }
            }
        }, 10000, 10000); // Start po 10s, powtarzaj co 10s

        System.out.println("[BATTLESHIP SERVER]: Connection monitor started");
    }

    private void cleanupEmptyGames() {
        activeGames.entrySet().removeIf(entry -> {
            String gameId = entry.getKey();
            Map<Integer, BattleshipClientHandler> connections = gameConnections.get(gameId);

            if (connections == null || connections.isEmpty()) {
                System.out.println("[BATTLESHIP SERVER]: Removing empty game: " + gameId);
                gameConnections.remove(gameId);
                return true;
            }
            return false;
        });
    }

    public void stopServer() {
        running = false;

        // DODAJ: Zatrzymaj monitor połączeń
        if (connectionMonitor != null) {
            connectionMonitor.cancel();
            System.out.println("[BATTLESHIP SERVER]: Connection monitor stopped");
        }

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

        // Pobierz mapę połączeń dla gry
        Map<Integer, BattleshipClientHandler> connections = gameConnections.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>());

        // KRYTYCZNE: Sprawdź czy gracz już jest połączony
        BattleshipClientHandler existingHandler = connections.get(playerId);
        if (existingHandler != null) {
            System.out.println("[BATTLESHIP SERVER]: Player " + playerId + " already connected - replacing handler");
            try {
                // Zamknij stare połączenie
                existingHandler.cleanup();
            } catch (Exception e) {
                System.err.println("[BATTLESHIP SERVER]: Error closing old connection: " + e.getMessage());
            }
        }

        // Dodaj nowe połączenie gracza
        connections.put(playerId, handler);
        System.out.println("[BATTLESHIP SERVER]: Player " + playerId + " connected to game " + gameId);
        System.out.println("[BATTLESHIP SERVER]: Total connections for game: " + connections.size());

        // Pobierz lub utwórz grę
        BattleshipGame game = activeGames.computeIfAbsent(gameId, k -> {
            System.out.println("[BATTLESHIP SERVER]: Creating new game: " + gameId);
            return new BattleshipGame(gameId);
        });

        // Dodaj gracza do gry
        boolean added = game.addPlayer(playerId);
        System.out.println("[BATTLESHIP SERVER]: Player " + playerId +
                (added ? " successfully added" : " already in game or game full") + " to game " + gameId);

        System.out.println("[BATTLESHIP SERVER]: Current game state: " + game.getState());
        System.out.println("[BATTLESHIP SERVER]: Players in game: " + game.getPlayerBoards().keySet());
        System.out.println("[BATTLESHIP SERVER]: Players ready: " + game.getPlayersReady());

        // KRYTYCZNE: Wyślij update NATYCHMIAST do wszystkich graczy
        GameUpdateMessage updateMessage = new GameUpdateMessage(game);

        // DODAJ: Małe opóźnienie na ustabilizowanie połączenia
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("[BATTLESHIP SERVER]: Broadcasting game update to " + connections.size() + " players");

        // Wyślij do każdego gracza z retry mechanism
        for (Map.Entry<Integer, BattleshipClientHandler> entry : connections.entrySet()) {
            int targetPlayerId = entry.getKey();
            BattleshipClientHandler targetHandler = entry.getValue();

            // Wyślij w osobnym wątku, żeby nie blokować innych
            executor.submit(() -> {
                try {
                    System.out.println("[BATTLESHIP SERVER]: Sending update to player " + targetPlayerId);

                    // Sprawdź czy handler jest żywy
                    if (targetHandler.isConnected()) {
                        targetHandler.sendMessage(updateMessage);
                        System.out.println("[BATTLESHIP SERVER]: Update sent successfully to player " + targetPlayerId);
                    } else {
                        System.err.println("[BATTLESHIP SERVER]: Handler for player " + targetPlayerId + " is not connected");
                        connections.remove(targetPlayerId);
                    }

                } catch (Exception e) {
                    System.err.println("[BATTLESHIP SERVER]: Failed to send update to player " + targetPlayerId + ": " + e.getMessage());
                    // Nie usuwaj od razu - może to być tymczasowy problem
                }
            });
        }

        // DODAJ: Drugi broadcast po 2 sekundach dla pewności
        executor.submit(() -> {
            try {
                Thread.sleep(2000);
                System.out.println("[BATTLESHIP SERVER]: === SECOND BROADCAST ===");

                Map<Integer, BattleshipClientHandler> currentConnections = gameConnections.get(gameId);
                if (currentConnections != null && !currentConnections.isEmpty()) {
                    BattleshipGame currentGame = activeGames.get(gameId);
                    if (currentGame != null) {
                        GameUpdateMessage secondUpdate = new GameUpdateMessage(currentGame);

                        for (Map.Entry<Integer, BattleshipClientHandler> entry : currentConnections.entrySet()) {
                            try {
                                if (entry.getValue().isConnected()) {
                                    entry.getValue().sendMessage(secondUpdate);
                                    System.out.println("[BATTLESHIP SERVER]: Second update sent to player " + entry.getKey());
                                }
                            } catch (Exception e) {
                                System.err.println("[BATTLESHIP SERVER]: Second update failed for player " + entry.getKey());
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

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

    public void checkConnections() {
        System.out.println("[BATTLESHIP SERVER]: === CONNECTION CHECK ===");

        for (Map.Entry<String, Map<Integer, BattleshipClientHandler>> gameEntry : gameConnections.entrySet()) {
            String gameId = gameEntry.getKey();
            Map<Integer, BattleshipClientHandler> connections = gameEntry.getValue();

            System.out.println("[BATTLESHIP SERVER]: Checking " + connections.size() + " connections for game: " + gameId);

            // Usuń martwe połączenia
            connections.entrySet().removeIf(entry -> {
                int playerId = entry.getKey();
                BattleshipClientHandler handler = entry.getValue();

                try {
                    // Sprawdź czy handler jest żywy
                    if (handler == null || !handler.isConnected()) {
                        System.out.println("[BATTLESHIP SERVER]: Removing dead connection for player " + playerId + " in game " + gameId);
                        return true; // Usuń połączenie
                    }

                    // Wyślij ping (game update) żeby sprawdzić połączenie
                    BattleshipGame game = activeGames.get(gameId);
                    if (game != null) {
                        handler.sendMessage(new GameUpdateMessage(game));
                        return false; // Połączenie działa
                    }

                } catch (Exception e) {
                    System.out.println("[BATTLESHIP SERVER]: Exception checking player " + playerId + ": " + e.getMessage());
                    return true; // Usuń problematyczne połączenie
                }

                return false;
            });

            System.out.println("[BATTLESHIP SERVER]: After cleanup: " + connections.size() + " connections remain for game " + gameId);
        }

        System.out.println("[BATTLESHIP SERVER]: === CONNECTION CHECK COMPLETE ===");
    }

    public BattleshipGame getGame(String gameId) {
        return activeGames.get(gameId);
    }
}