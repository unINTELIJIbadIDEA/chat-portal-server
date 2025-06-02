package com.project.server;

import com.project.models.battleship.*;
import com.project.models.battleship.messages.*;
import com.project.services.BattleshipGameService;
import com.project.utils.Config;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final Map<String, BattleshipGame> pausedGameStates = new ConcurrentHashMap<>();
    private final Map<String, Set<Integer>> activePlayersInGame = new ConcurrentHashMap<>();
    // Połączenia graczy w grach
    private final Map<String, Map<Integer, BattleshipClientHandler>> gameConnections = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, Long>> lastPingTimes = new ConcurrentHashMap<>();
    private static final long PING_INTERVAL = 2000; // 2 sekundy
    private static final long PING_TIMEOUT = 8000; // 8 sekund timeout

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

        // Sprawdzaj połączenia co 5 sekund
        connectionMonitor.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                try {
                    System.out.println("[BATTLESHIP SERVER]: Running connection check...");
                    checkConnections();
                    sendPingToAllPlayers();
                    checkPingTimeouts();
                    cleanupEmptyGames();
                } catch (Exception e) {
                    System.err.println("[BATTLESHIP SERVER]: Error during connection check: " + e.getMessage());
                }
            }
        }, 2000, 2000); // ZMIEŃ NA 2 sekundy zamiast 5

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

        // Pobierz lub utwórz mapę połączeń dla gry
        Map<Integer, BattleshipClientHandler> connections = gameConnections.computeIfAbsent(
                gameId, k -> new ConcurrentHashMap<>()
        );

        // Dodaj połączenie gracza
        connections.put(playerId, handler);
        System.out.println("[BATTLESHIP SERVER]: Player " + playerId + " connected to game " + gameId);

        // Dodaj do aktywnych graczy
        activePlayersInGame.computeIfAbsent(gameId, k -> ConcurrentHashMap.newKeySet()).add(playerId);

        // Zainicjalizuj ping tracking
        lastPingTimes.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>())
                .put(playerId, System.currentTimeMillis());

        System.out.println("[BATTLESHIP SERVER]: Ping tracking initialized for player " + playerId);

        // Sprawdź czy to rejoin do pauzowanej gry
        try {
            if (gameService.getGameInfoDirect(gameId) != null &&
                    "PAUSED".equals(gameService.getGameInfoDirect(gameId).getStatus())) {

                System.out.println("[BATTLESHIP SERVER]: Player rejoining paused game: " + gameId);
                handleGameRejoin(gameId, playerId, handler);
                return;
            }
        } catch (SQLException e) {
            System.err.println("[BATTLESHIP SERVER]: Error checking game status: " + e.getMessage());
        }

        // Pobierz lub utwórz grę (normalne flow)
        BattleshipGame game = activeGames.computeIfAbsent(
                gameId, k -> restoreOrCreateGame(gameId)
        );

        // Dodaj gracza do gry
        boolean added = game.addPlayer(playerId);
        System.out.println("[BATTLESHIP SERVER]: Player " + playerId +
                (added ? " successfully added" : " already in game or game full") + " to game " + gameId);

        // Wyślij update
        GameUpdateMessage updateMessage = new GameUpdateMessage(game);
        broadcastToGame(gameId, updateMessage);

        if (game.getState() == GameState.SHIP_PLACEMENT) {
            broadcastGameStateChange(gameId, game.getState());
        }
    }

    private void broadcastGameStateChange(String gameId, GameState newState) {
        System.out.println("[BATTLESHIP SERVER]: Broadcasting state change to " + newState + " for game " + gameId);

        Map<Integer, BattleshipClientHandler> connections = gameConnections.get(gameId);
        if (connections != null) {
            // Użyj dedykowanej wiadomości o zmianie stanu (jeśli istnieje)
            // lub wyślij kolejny GameUpdate dla pewności
            BattleshipGame game = activeGames.get(gameId);
            if (game != null) {
                GameUpdateMessage stateUpdate = new GameUpdateMessage(game);
                connections.values().forEach(handler -> {
                    try {
                        handler.sendMessage(stateUpdate);
                        System.out.println("[BATTLESHIP SERVER]: State change sent to player");
                    } catch (Exception e) {
                        System.err.println("[BATTLESHIP SERVER]: Failed to send state change: " + e.getMessage());
                    }
                });
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
            case PLAYER_READY:  // NOWY CASE
                handlePlayerReady(gameId, game, (PlayerReadyMessage) message);
                break;
            default:
                System.err.println("[BATTLESHIP SERVER]: Unknown message type: " + message.getType());
                break;
        }
    }

    private void handlePlayerReady(String gameId, BattleshipGame game, PlayerReadyMessage message) {
        System.out.println("[BATTLESHIP SERVER]: === PLAYER READY ===");
        System.out.println("[BATTLESHIP SERVER]: Player " + message.getPlayerId() + " is ready in game " + gameId);

        // Sprawdź czy gracz ma wszystkie statki
        GameBoard playerBoard = game.getPlayerBoards().get(message.getPlayerId());
        if (playerBoard == null) {
            System.err.println("[BATTLESHIP SERVER]: Player board not found for player " + message.getPlayerId());
            return;
        }

        if (!playerBoard.allShipsPlaced()) {
            System.err.println("[BATTLESHIP SERVER]: Player " + message.getPlayerId() +
                    " is not ready - not all ships placed!");
            return;
        }

        // Oznacz gracza jako gotowego
        game.getPlayersReady().put(message.getPlayerId(), true);
        System.out.println("[BATTLESHIP SERVER]: Player " + message.getPlayerId() + " marked as ready");
        System.out.println("[BATTLESHIP SERVER]: Players ready status: " + game.getPlayersReady());

        // Sprawdź czy wszyscy są gotowi
        boolean allReady = game.getPlayersReady().size() == 2 &&
                game.getPlayersReady().values().stream().allMatch(ready -> ready);

        if (allReady) {
            System.out.println("[BATTLESHIP SERVER]: All players ready! Starting game...");

            // Zmień stan gry na PLAYING
            game.setState(GameState.PLAYING);

            // Ustaw pierwszego gracza
            if (game.getCurrentPlayer() == -1) {
                int firstPlayer = game.getPlayerBoards().keySet().iterator().next();
                game.setCurrentPlayer(firstPlayer);
                System.out.println("[BATTLESHIP SERVER]: First player set to: " + firstPlayer);
            }

            // Zaktualizuj bazę danych
            try {
                gameService.updateGameStatus(gameId, "PLAYING", null);
                System.out.println("[BATTLESHIP SERVER]: Database updated to PLAYING status");
            } catch (Exception e) {
                System.err.println("[BATTLESHIP SERVER]: Failed to update database: " + e.getMessage());
            }
        }

        // Wyślij update do wszystkich graczy
        GameUpdateMessage updateMessage = new GameUpdateMessage(game);
        broadcastToGame(gameId, updateMessage);

        // Jeśli gra się rozpoczęła, wyślij dodatkowy update po małym opóźnieniu
        if (game.getState() == GameState.PLAYING) {
            new Thread(() -> {
                try {
                    Thread.sleep(200);
                    broadcastToGame(gameId, updateMessage);
                    broadcastGameStateChange(gameId, GameState.PLAYING);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
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

            // Pobierz planszę gracza
            GameBoard playerBoard = game.getPlayerBoards().get(message.getPlayerId());
            if (playerBoard != null) {
                boolean allShipsPlaced = playerBoard.allShipsPlaced();
                System.out.println("[BATTLESHIP SERVER]: Player " + message.getPlayerId() +
                        " - all ships placed: " + allShipsPlaced);
            }

            // Sprawdź czy wszyscy gracze są gotowi
            boolean allReady = game.getPlayersReady().values().stream()
                    .filter(ready -> ready != null)
                    .allMatch(ready -> ready);
            System.out.println("[BATTLESHIP SERVER]: All players ready: " + allReady);

            // WAŻNE: Broadcast update do wszystkich graczy w tej grze
            GameUpdateMessage updateMessage = new GameUpdateMessage(game);
            broadcastToGame(gameId, updateMessage);

            // Jeśli gra przeszła do stanu PLAYING
            if (game.getState() == GameState.PLAYING) {
                System.out.println("[BATTLESHIP SERVER]: === GAME STARTING ===");
                System.out.println("[BATTLESHIP SERVER]: Game " + gameId + " is now in PLAYING state!");
                System.out.println("[BATTLESHIP SERVER]: Current player: " + game.getCurrentPlayer());

                // Wyślij dodatkowy update żeby upewnić się że klienci otrzymali zmianę stanu
                try {
                    Thread.sleep(100); // Małe opóźnienie
                    broadcastToGame(gameId, updateMessage);

                    // Zaktualizuj bazę danych
                    gameService.updateGameStatus(gameId, "PLAYING", null);
                    System.out.println("[BATTLESHIP SERVER]: Database updated to PLAYING status");

                    // Wyślij specjalną wiadomość o rozpoczęciu gry
                    broadcastGameStateChange(gameId, GameState.PLAYING);

                } catch (Exception e) {
                    System.err.println("[BATTLESHIP SERVER]: Error during game start: " + e.getMessage());
                }
            }
        } else {
            System.err.println("[BATTLESHIP SERVER]: Failed to place ship for player " + message.getPlayerId());
            // Możesz wysłać błąd do gracza
        }
    }

    private void handleTakeShot(String gameId, BattleshipGame game, TakeShotMessage message) {
        ShotResult result = game.takeShot(message.getPlayerId(), message.getX(), message.getY());

        System.out.println("[BATTLESHIP SERVER]: Shot result " + result + " for player " + message.getPlayerId() +
                " at (" + message.getX() + "," + message.getY() + ") in game " + gameId);

        // Wyślij wynik strzału
        broadcastToGame(gameId, new ShotResultMessage(message.getPlayerId(), gameId, result, message.getX(), message.getY()));

        // NOWE: Jeśli statek został zatopiony, wyślij pozycje całego statku
        if (result == ShotResult.SUNK || result == ShotResult.GAME_OVER) {
            // Znajdź przeciwnika
            int opponentId = game.getPlayerBoards().keySet().stream()
                    .filter(id -> id != message.getPlayerId())
                    .findFirst()
                    .orElse(-1);

            if (opponentId != -1) {
                GameBoard opponentBoard = game.getPlayerBoards().get(opponentId);
                Ship sunkShip = opponentBoard.getSunkShipAt(message.getX(), message.getY());

                if (sunkShip != null) {
                    List<Position> shipPositions = opponentBoard.getSunkShipPositions(sunkShip);
                    ShipSunkMessage shipSunkMsg = new ShipSunkMessage(message.getPlayerId(), gameId, shipPositions);
                    broadcastToGame(gameId, shipSunkMsg);
                    System.out.println("[BATTLESHIP SERVER]: Sent sunk ship positions: " + shipPositions.size() + " cells");
                }
            }
        }

        // Wyślij zaktualizowany stan gry
        broadcastToGame(gameId, new GameUpdateMessage(game));

        // Jeśli gra się skończyła
        if (result == ShotResult.GAME_OVER) {
            System.out.println("[BATTLESHIP SERVER]: === GAME FINISHED ===");
            System.out.println("[BATTLESHIP SERVER]: Winner: " + message.getPlayerId() + " in game " + gameId);

            try {
                // Zaktualizuj status w bazie danych
                gameService.updateGameStatus(gameId, "FINISHED", message.getPlayerId());
                System.out.println("[BATTLESHIP SERVER]: Database updated - game finished, winner: " + message.getPlayerId());

                // Wyślij powiadomienie na czat o zakończeniu gry
                notifyChatAboutGameEnd(gameId, message.getPlayerId());

            } catch (Exception e) {
                System.err.println("[BATTLESHIP SERVER]: Failed to update game status: " + e.getMessage());
            }
        }
    }

    private void notifyChatAboutGameEnd(String gameId, int winnerId) {
        try {
            // Pobierz informacje o grze z bazy
            BattleshipGameInfo gameInfo = gameService.getGameInfoDirect(gameId);
            if (gameInfo == null) return;

            String chatId = gameInfo.getChatId();

            // Wyślij powiadomienie przez połączenie TCP
            java.net.Socket notificationSocket = new java.net.Socket("localhost", Config.getLOCAL_SERVER_PORT());

            java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(notificationSocket.getOutputStream());
            out.flush();

            // Wiadomość dołączenia do pokoju (jako zwycięzca)
            String token = ApiServer.getTokenManager().generateToken(String.valueOf(winnerId));
            com.project.models.message.ClientMessage joinMessage =
                    new com.project.models.message.ClientMessage("/join " + chatId, chatId, token);
            out.writeObject(joinMessage);
            out.flush();

            Thread.sleep(100);

            // Wiadomość o zakończeniu gry
            String gameEndMessage = "🎉 Gra w statki zakończona! Wygrał gracz " + winnerId + " 🏆";
            com.project.models.message.ClientMessage endMessage =
                    new com.project.models.message.ClientMessage(gameEndMessage, chatId, token);
            out.writeObject(endMessage);
            out.flush();

            out.close();
            notificationSocket.close();

            System.out.println("[BATTLESHIP SERVER]: Game end notification sent to chat: " + chatId);

        } catch (Exception e) {
            System.err.println("[BATTLESHIP SERVER]: Failed to notify chat about game end: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void broadcastToGame(String gameId, BattleshipMessage message) {
        Map<Integer, BattleshipClientHandler> connections = gameConnections.get(gameId);
        if (connections != null) {
            System.out.println("[BATTLESHIP SERVER]: Broadcasting " + message.getType() +
                    " to " + connections.size() + " players in game " + gameId);

            // Wyślij do każdego gracza z małym opóźnieniem
            connections.forEach((playerId, handler) -> {
                new Thread(() -> {
                    try {
                        // Sprawdź czy handler jest aktywny
                        if (handler != null && handler.isRunning()) {
                            handler.sendMessage(message);
                            System.out.println("[BATTLESHIP SERVER]: Message sent to player " + playerId);

                            // Małe opóźnienie między wysyłkami
                            Thread.sleep(50);
                        } else {
                            System.err.println("[BATTLESHIP SERVER]: Handler for player " + playerId + " is not active");
                        }
                    } catch (Exception e) {
                        System.err.println("[BATTLESHIP SERVER]: Failed to send to player " + playerId + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }).start();
            });
        } else {
            System.err.println("[BATTLESHIP SERVER]: No connections found for game " + gameId);
        }
    }

    public void removePlayerFromGame(String gameId, int playerId) {
        System.out.println("[BATTLESHIP SERVER]: === REMOVING PLAYER FROM GAME ===");
        System.out.println("[BATTLESHIP SERVER]: Removing player " + playerId + " from game " + gameId);

        // Obsłuż rozłączenie gracza
        handlePlayerDisconnection(gameId, playerId);

        Map<Integer, BattleshipClientHandler> connections = gameConnections.get(gameId);
        if (connections != null) {
            connections.remove(playerId);

            // Nie usuwaj gry natychmiast - może być wznowiona
            if (connections.isEmpty()) {
                // Sprawdź czy gra może być wznowiona w przyszłości
                BattleshipGame game = activeGames.get(gameId);
                if (game != null && (game.getState() == GameState.PLAYING || game.getState() == GameState.PAUSED)) {
                    System.out.println("[BATTLESHIP SERVER]: Keeping game " + gameId + " for potential rejoin");
                    // Nie usuwaj activeGames.remove(gameId) - zostaw dla rejoin
                } else {
                    // Usuń tylko jeśli gra nie była w trakcie
                    activeGames.remove(gameId);
                    gameConnections.remove(gameId);
                    System.out.println("[BATTLESHIP SERVER]: Game " + gameId + " removed - no players left and not in progress");
                }
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

    public void verifyConnections(String gameId) {
        Map<Integer, BattleshipClientHandler> connections = gameConnections.get(gameId);
        if (connections != null) {
            System.out.println("[BATTLESHIP SERVER]: === VERIFYING CONNECTIONS ===");
            connections.forEach((playerId, handler) -> {
                boolean active = handler != null && handler.isRunning();
                System.out.println("[BATTLESHIP SERVER]: Player " + playerId + " - handler active: " + active);
            });
        }
    }

    private BattleshipGame restoreOrCreateGame(String gameId) {
        // Sprawdź czy jest zapisany stan gry
        if (pausedGameStates.containsKey(gameId)) {
            System.out.println("[BATTLESHIP SERVER]: Restoring saved game state for: " + gameId);
            return pausedGameStates.get(gameId);
        }

        // Utwórz nową grę
        System.out.println("[BATTLESHIP SERVER]: Creating new game: " + gameId);
        return new BattleshipGame(gameId);
    }

    private void handleGameRejoin(String gameId, int playerId, BattleshipClientHandler handler) {
        System.out.println("[BATTLESHIP SERVER]: === GAME REJOIN ===");

        // Przywróć zapisaną grę
        BattleshipGame game = pausedGameStates.get(gameId);
        if (game != null) {
            activeGames.put(gameId, game);

            // Sprawdź czy wszyscy gracze są z powrotem
            Set<Integer> activePlayer = activePlayersInGame.get(gameId);
            Set<Integer> gamePlayers = game.getPlayerBoards().keySet();

            if (activePlayer != null && activePlayer.containsAll(gamePlayers)) {
                System.out.println("[BATTLESHIP SERVER]: All players reconnected, resuming game");

                // Wznów grę w bazie danych
                try {
                    gameService.resumeGame(gameId);
                    game.setState(GameState.PLAYING);
                    pausedGameStates.remove(gameId);
                } catch (SQLException e) {
                    System.err.println("[BATTLESHIP SERVER]: Error resuming game: " + e.getMessage());
                }
            }

            // Wyślij aktualny stan gry
            GameUpdateMessage updateMessage = new GameUpdateMessage(game);
            broadcastToGame(gameId, updateMessage);
        }
    }

    private void handlePlayerDisconnection(String gameId, int playerId) {
        System.out.println("[BATTLESHIP SERVER]: === PLAYER DISCONNECTION ===");
        System.out.println("[BATTLESHIP SERVER]: Player " + playerId + " disconnected from game " + gameId);

        // Usuń z aktywnych graczy
        Set<Integer> activePlayers = activePlayersInGame.get(gameId);
        if (activePlayers != null) {
            activePlayers.remove(playerId);

            BattleshipGame game = activeGames.get(gameId);
            if (game != null && game.getState() == GameState.PLAYING) {
                // Jeśli gra była w trakcie, zapauzuj ją
                System.out.println("[BATTLESHIP SERVER]: Pausing game due to player disconnection");

                // Zapisz stan gry
                pausedGameStates.put(gameId, game);

                // Zapauzuj w bazie danych
                try {
                    gameService.pauseGame(gameId);
                    game.setState(GameState.PAUSED); // Dodaj PAUSED do GameState enum
                } catch (SQLException e) {
                    System.err.println("[BATTLESHIP SERVER]: Error pausing game: " + e.getMessage());
                }

                // Powiadom pozostałych graczy
                broadcastToGame(gameId, new GameUpdateMessage(game));
            }
        }
    }

    // DODAJ TE METODY PRZED metodą stopServer():

    private void sendPingToAllPlayers() {
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<String, Map<Integer, BattleshipClientHandler>> gameEntry : gameConnections.entrySet()) {
            String gameId = gameEntry.getKey();
            Map<Integer, BattleshipClientHandler> connections = gameEntry.getValue();

            for (Map.Entry<Integer, BattleshipClientHandler> playerEntry : connections.entrySet()) {
                int playerId = playerEntry.getKey();
                BattleshipClientHandler handler = playerEntry.getValue();

                if (handler != null && handler.isConnected()) {
                    // Wyślij ping (GameUpdate jako ping)
                    BattleshipGame game = activeGames.get(gameId);
                    if (game != null) {
                        try {
                            handler.sendMessage(new GameUpdateMessage(game));

                            // Zaktualizuj czas ostatniego ping
                            lastPingTimes.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>())
                                    .put(playerId, currentTime);

                        } catch (Exception e) {
                            System.err.println("[BATTLESHIP SERVER]: Failed to send ping to player " + playerId + ": " + e.getMessage());
                            // Oznacz jako rozłączonego
                            markPlayerAsDisconnected(gameId, playerId);
                        }
                    }
                }
            }
        }
    }

    private void checkPingTimeouts() {
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<String, Map<Integer, Long>> gameEntry : lastPingTimes.entrySet()) {
            String gameId = gameEntry.getKey();
            Map<Integer, Long> playerPings = gameEntry.getValue();

            for (Map.Entry<Integer, Long> playerEntry : playerPings.entrySet()) {
                int playerId = playerEntry.getKey();
                long lastPing = playerEntry.getValue();

                if (currentTime - lastPing > PING_TIMEOUT) {
                    System.out.println("[BATTLESHIP SERVER]: Player " + playerId + " in game " + gameId + " timed out");
                    markPlayerAsDisconnected(gameId, playerId);
                }
            }
        }
    }

    private void markPlayerAsDisconnected(String gameId, int playerId) {
        System.out.println("[BATTLESHIP SERVER]: === MARKING PLAYER AS DISCONNECTED ===");
        System.out.println("[BATTLESHIP SERVER]: Player " + playerId + " in game " + gameId);

        // Usuń z połączeń
        Map<Integer, BattleshipClientHandler> connections = gameConnections.get(gameId);
        if (connections != null) {
            BattleshipClientHandler handler = connections.remove(playerId);
            if (handler != null) {
                try {
                    if (!handler.isConnected()) {
                        System.out.println("[BATTLESHIP SERVER]: Handler already disconnected, closing gracefully");
                    }
                } catch (Exception e) {
                    // Ignoruj błędy przy sprawdzaniu połączenia
                }
            }
        }

        // Obsłuż rozłączenie
        handlePlayerDisconnection(gameId, playerId);

        // Usuń z ping timers
        Map<Integer, Long> gamePings = lastPingTimes.get(gameId);
        if (gamePings != null) {
            gamePings.remove(playerId);
            if (gamePings.isEmpty()) {
                lastPingTimes.remove(gameId);
            }
        }
    }

}