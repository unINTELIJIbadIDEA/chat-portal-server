package com.project.server;

import com.project.config.ConfigProperties;
import com.project.models.battleship.*;
import com.project.models.battleship.messages.*;
import com.project.services.BattleshipGameService;
import com.project.config.ConfigProperties;

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
import java.util.logging.*;

public class BattleshipServer {
    private static final Logger logger = Logger.getLogger(BattleshipServer.class.getName());

    static {
        try {
            FileHandler fileHandler = new FileHandler("logs/battleship.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.INFO);
        } catch (IOException e) {
            System.err.println("Logger initialization failed for BattleshipServer: " + e.getMessage());
        }
    }

    private static final BattleshipServer INSTANCE = new BattleshipServer();

    private final ExecutorService executor;
    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private final BattleshipGameService gameService = new BattleshipGameService();
    private java.util.Timer connectionMonitor;


    private final Map<String, BattleshipGame> activeGames = new ConcurrentHashMap<>();
    private final Map<String, BattleshipGame> pausedGameStates = new ConcurrentHashMap<>();
    private final Map<String, Set<Integer>> activePlayersInGame = new ConcurrentHashMap<>();

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
            serverSocket = new ServerSocket(ConfigProperties.getBATTLESHIP_SERVER_PORT());
            logger.info("[BATTLESHIP SERVER]: Server started on port " + ConfigProperties.getBATTLESHIP_SERVER_PORT());

            startConnectionMonitor();

            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    logger.info("[BATTLESHIP SERVER]: New connection from " + socket.getRemoteSocketAddress());
                    executor.submit(new BattleshipClientHandler(socket, this));
                } catch (IOException e) {
                    if (running) {
                        logger.severe("[BATTLESHIP SERVER]: Error accepting connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            logger.severe("[BATTLESHIP SERVER]: Failed to start: " + e.getMessage());
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
                    logger.info("[BATTLESHIP SERVER]: Running connection check...");
                    checkConnections();
                    sendPingToAllPlayers();
                    checkPingTimeouts();
                    cleanupEmptyGames();
                } catch (Exception e) {
                    logger.severe("[BATTLESHIP SERVER]: Error during connection check: " + e.getMessage());
                }
            }
        }, 2000, 2000);

        logger.info("[BATTLESHIP SERVER]: Connection monitor started");
    }

    private void cleanupEmptyGames() {
        activeGames.entrySet().removeIf(entry -> {
            String gameId = entry.getKey();
            Map<Integer, BattleshipClientHandler> connections = gameConnections.get(gameId);

            if (connections == null || connections.isEmpty()) {
                logger.info("[BATTLESHIP SERVER]: Removing empty game: " + gameId);
                gameConnections.remove(gameId);
                return true;
            }
            return false;
        });
    }

    public void stopServer() {
        running = false;

        if (connectionMonitor != null) {
            connectionMonitor.cancel();
            logger.info("[BATTLESHIP SERVER]: Connection monitor stopped");
        }

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                logger.info("[BATTLESHIP SERVER]: Socket closed.");
            } catch (IOException e) {
                logger.severe("[BATTLESHIP SERVER]: Error closing socket: " + e.getMessage());
            }
        }
        executor.shutdownNow();
        logger.info("[BATTLESHIP SERVER]: Executor shutdown.");
    }

    public void registerGame(String gameId) {
        logger.info("[BATTLESHIP SERVER]: Registering game: " + gameId);

        // Sprawdź czy gra już istnieje
        if (activeGames.containsKey(gameId)) {
            logger.info("[BATTLESHIP SERVER]: Game " + gameId + " already registered");
            return;
        }

        // Utwórz nową grę
        activeGames.put(gameId, new BattleshipGame(gameId));
        gameConnections.put(gameId, new ConcurrentHashMap<>());
        logger.info("[BATTLESHIP SERVER]: Registered new game: " + gameId);
    }

    public void addPlayerToGame(String gameId, int playerId) {
        BattleshipGame game = activeGames.get(gameId);
        if (game != null) {
            game.addPlayer(playerId);
            logger.info("[BATTLESHIP SERVER]: Player " + playerId + " added to game " + gameId);
        }
    }

    public void handlePlayerConnection(String gameId, int playerId, BattleshipClientHandler handler) {
        logger.info("[BATTLESHIP SERVER]: === PLAYER CONNECTION ===");
        logger.info("[BATTLESHIP SERVER]: Game ID: " + gameId);
        logger.info("[BATTLESHIP SERVER]: Player ID: " + playerId);

        // Pobierz lub utwórz mapę połączeń dla gry
        Map<Integer, BattleshipClientHandler> connections = gameConnections.computeIfAbsent(
                gameId, k -> new ConcurrentHashMap<>()
        );

        // Dodaj połączenie gracza
        connections.put(playerId, handler);
        logger.info("[BATTLESHIP SERVER]: Player " + playerId + " connected to game " + gameId);

        // Dodaj do aktywnych graczy
        activePlayersInGame.computeIfAbsent(gameId, k -> ConcurrentHashMap.newKeySet()).add(playerId);

        // Zainicjalizuj ping tracking
        lastPingTimes.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>())
                .put(playerId, System.currentTimeMillis());

        logger.info("[BATTLESHIP SERVER]: Ping tracking initialized for player " + playerId);

        // Sprawdź czy to rejoin do pauzowanej gry
        try {
            if (gameService.getGameInfoDirect(gameId) != null &&
                    "PAUSED".equals(gameService.getGameInfoDirect(gameId).getStatus())) {

                logger.info("[BATTLESHIP SERVER]: Player rejoining paused game: " + gameId);
                handleGameRejoin(gameId, playerId, handler);
                return;
            }
        } catch (SQLException e) {
            logger.severe("[BATTLESHIP SERVER]: Error checking game status: " + e.getMessage());
        }

        // Pobierz lub utwórz grę
        BattleshipGame game = activeGames.computeIfAbsent(
                gameId, k -> restoreOrCreateGame(gameId)
        );

        // Dodaj gracza do gry
        boolean added = game.addPlayer(playerId);
        logger.info("[BATTLESHIP SERVER]: Player " + playerId +
                (added ? " successfully added" : " already in game or game full") + " to game " + gameId);

        // Wyślij update
        GameUpdateMessage updateMessage = new GameUpdateMessage(game);
        broadcastToGame(gameId, updateMessage);

        if (game.getState() == GameState.SHIP_PLACEMENT) {
            broadcastGameStateChange(gameId, game.getState());
        }
    }

    private void broadcastGameStateChange(String gameId, GameState newState) {
        logger.info("[BATTLESHIP SERVER]: Broadcasting state change to " + newState + " for game " + gameId);

        Map<Integer, BattleshipClientHandler> connections = gameConnections.get(gameId);
        if (connections != null) {
            BattleshipGame game = activeGames.get(gameId);
            if (game != null) {
                GameUpdateMessage stateUpdate = new GameUpdateMessage(game);
                connections.values().forEach(handler -> {
                    try {
                        handler.sendMessage(stateUpdate);
                        logger.info("[BATTLESHIP SERVER]: State change sent to player");
                    } catch (Exception e) {
                        logger.severe("[BATTLESHIP SERVER]: Failed to send state change: " + e.getMessage());
                    }
                });
            }
        }
    }

    public void handleBattleshipMessage(String gameId, BattleshipMessage message) {
        BattleshipGame game = activeGames.get(gameId);
        if (game == null) {
            logger.warning("[BATTLESHIP SERVER]: Game not found: " + gameId);
            return;
        }

        switch (message.getType()) {
            case PLACE_SHIP:
                handlePlaceShip(gameId, game, (PlaceShipMessage) message);
                break;
            case TAKE_SHOT:
                handleTakeShot(gameId, game, (TakeShotMessage) message);
                break;
            case PLAYER_READY:
                handlePlayerReady(gameId, game, (PlayerReadyMessage) message);
                break;
            default:
                logger.warning("[BATTLESHIP SERVER]: Unknown message type: " + message.getType());
                break;
        }
    }

    private void handlePlayerReady(String gameId, BattleshipGame game, PlayerReadyMessage message) {
        logger.info("[BATTLESHIP SERVER]: === PLAYER READY ===");
        logger.info("[BATTLESHIP SERVER]: Player " + message.getPlayerId() + " is ready in game " + gameId);

        // Sprawdź czy gracz ma wszystkie statki
        GameBoard playerBoard = game.getPlayerBoards().get(message.getPlayerId());
        if (playerBoard == null) {
            logger.warning("[BATTLESHIP SERVER]: Player board not found for player " + message.getPlayerId());
            return;
        }

        if (!playerBoard.allShipsPlaced()) {
            logger.warning("[BATTLESHIP SERVER]: Player " + message.getPlayerId() +
                    " is not ready - not all ships placed!");
            return;
        }

        // Oznacz gracza jako gotowego
        game.getPlayersReady().put(message.getPlayerId(), true);
        logger.info("[BATTLESHIP SERVER]: Player " + message.getPlayerId() + " marked as ready");
        logger.info("[BATTLESHIP SERVER]: Players ready status: " + game.getPlayersReady());

        // Sprawdź czy wszyscy są gotowi
        boolean allReady = game.getPlayersReady().size() == 2 &&
                game.getPlayersReady().values().stream().allMatch(ready -> ready);

        if (allReady) {
            logger.info("[BATTLESHIP SERVER]: All players ready! Starting game...");


            game.setState(GameState.PLAYING);


            if (game.getCurrentPlayer() == -1) {
                int firstPlayer = game.getPlayerBoards().keySet().iterator().next();
                game.setCurrentPlayer(firstPlayer);
                logger.info("[BATTLESHIP SERVER]: First player set to: " + firstPlayer);
            }


            try {
                gameService.updateGameStatus(gameId, "PLAYING", null);
                logger.info("[BATTLESHIP SERVER]: Database updated to PLAYING status");
            } catch (Exception e) {
                logger.severe("[BATTLESHIP SERVER]: Failed to update database: " + e.getMessage());
            }
        }


        GameUpdateMessage updateMessage = new GameUpdateMessage(game);
        broadcastToGame(gameId, updateMessage);


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
        logger.info("[BATTLESHIP SERVER]: === PLACE SHIP ===");
        logger.info("[BATTLESHIP SERVER]: Player " + message.getPlayerId() + " placing " +
                message.getShipType() + " at (" + message.getX() + "," + message.getY() + ")");

        boolean placed = game.placeShip(
                message.getPlayerId(),
                message.getShipType(),
                message.getX(),
                message.getY(),
                message.isHorizontal()
        );

        logger.info("[BATTLESHIP SERVER]: Ship placement " + (placed ? "successful" : "failed"));

        if (placed) {

            logger.info("[BATTLESHIP SERVER]: Current game state: " + game.getState());
            logger.info("[BATTLESHIP SERVER]: Players ready: " + game.getPlayersReady());


            GameBoard playerBoard = game.getPlayerBoards().get(message.getPlayerId());
            if (playerBoard != null) {
                boolean allShipsPlaced = playerBoard.allShipsPlaced();
                logger.info("[BATTLESHIP SERVER]: Player " + message.getPlayerId() +
                        " - all ships placed: " + allShipsPlaced);
            }


            boolean allReady = game.getPlayersReady().values().stream()
                    .filter(ready -> ready != null)
                    .allMatch(ready -> ready);
            logger.info("[BATTLESHIP SERVER]: All players ready: " + allReady);


            GameUpdateMessage updateMessage = new GameUpdateMessage(game);
            broadcastToGame(gameId, updateMessage);


            if (game.getState() == GameState.PLAYING) {
                logger.info("[BATTLESHIP SERVER]: === GAME STARTING ===");
                logger.info("[BATTLESHIP SERVER]: Game " + gameId + " is now in PLAYING state!");
                logger.info("[BATTLESHIP SERVER]: Current player: " + game.getCurrentPlayer());

                try {
                    Thread.sleep(100);
                    broadcastToGame(gameId, updateMessage);


                    gameService.updateGameStatus(gameId, "PLAYING", null);
                    logger.info("[BATTLESHIP SERVER]: Database updated to PLAYING status");


                    broadcastGameStateChange(gameId, GameState.PLAYING);

                } catch (Exception e) {
                    logger.severe("[BATTLESHIP SERVER]: Error during game start: " + e.getMessage());
                }
            }
        } else {
            logger.warning("[BATTLESHIP SERVER]: Failed to place ship for player " + message.getPlayerId());

        }
    }

    private void handleTakeShot(String gameId, BattleshipGame game, TakeShotMessage message) {
        ShotResult result = game.takeShot(message.getPlayerId(), message.getX(), message.getY());

        logger.info("[BATTLESHIP SERVER]: Shot result " + result + " for player " + message.getPlayerId() +
                " at (" + message.getX() + "," + message.getY() + ") in game " + gameId);

        broadcastToGame(gameId, new ShotResultMessage(message.getPlayerId(), gameId, result, message.getX(), message.getY()));


        if (result == ShotResult.SUNK || result == ShotResult.GAME_OVER) {

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
                    logger.info("[BATTLESHIP SERVER]: Sent sunk ship positions: " + shipPositions.size() + " cells");
                }
            }
        }


        broadcastToGame(gameId, new GameUpdateMessage(game));


        if (result == ShotResult.GAME_OVER) {
            logger.info("[BATTLESHIP SERVER]: === GAME FINISHED ===");
            logger.info("[BATTLESHIP SERVER]: Winner: " + message.getPlayerId() + " in game " + gameId);

            try {

                gameService.updateGameStatus(gameId, "FINISHED", message.getPlayerId());
                logger.info("[BATTLESHIP SERVER]: Database updated - game finished, winner: " + message.getPlayerId());


                notifyChatAboutGameEnd(gameId, message.getPlayerId());

            } catch (Exception e) {
                logger.severe("[BATTLESHIP SERVER]: Failed to update game status: " + e.getMessage());
            }
        }
    }

    private void notifyChatAboutGameEnd(String gameId, int winnerId) {
        try {

            BattleshipGameInfo gameInfo = gameService.getGameInfoDirect(gameId);
            if (gameInfo == null) return;

            String chatId = gameInfo.getChatId();


            String winnerNickname;
            try {
                winnerNickname = gameService.getUserNickname(winnerId);
            } catch (SQLException e) {
                logger.severe("[BATTLESHIP SERVER]: Error getting winner nickname: " + e.getMessage());
                winnerNickname = "Gracz " + winnerId; // Fallback
            }


            java.net.Socket notificationSocket = new java.net.Socket("localhost", ConfigProperties.getLOCAL_SERVER_PORT());

            java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(notificationSocket.getOutputStream());
            out.flush();


            String token = ApiServer.getTokenManager().generateToken(String.valueOf(winnerId));
            com.project.models.message.ClientMessage joinMessage =
                    new com.project.models.message.ClientMessage("/join " + chatId, chatId, token);
            out.writeObject(joinMessage);
            out.flush();

            Thread.sleep(100);


            String gameEndMessage = "🎉 Gra w statki zakończona! Wygrał " + winnerNickname + " 🏆";
            com.project.models.message.ClientMessage endMessage =
                    new com.project.models.message.ClientMessage(gameEndMessage, chatId, token);
            out.writeObject(endMessage);
            out.flush();

            out.close();
            notificationSocket.close();

            logger.info("[BATTLESHIP SERVER]: Game end notification sent to chat: " + chatId + " (winner: " + winnerNickname + ")");

        } catch (Exception e) {
            logger.severe("[BATTLESHIP SERVER]: Failed to notify chat about game end: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void broadcastToGame(String gameId, BattleshipMessage message) {
        Map<Integer, BattleshipClientHandler> connections = gameConnections.get(gameId);
        if (connections != null) {
            logger.info("[BATTLESHIP SERVER]: Broadcasting " + message.getType() +
                    " to " + connections.size() + " players in game " + gameId);

            connections.forEach((playerId, handler) -> {
                new Thread(() -> {
                    try {

                        if (handler != null && handler.isRunning()) {
                            handler.sendMessage(message);
                            logger.info("[BATTLESHIP SERVER]: Message sent to player " + playerId);

                            Thread.sleep(50);
                        } else {
                            logger.warning("[BATTLESHIP SERVER]: Handler for player " + playerId + " is not active");
                        }
                    } catch (Exception e) {
                        logger.severe("[BATTLESHIP SERVER]: Failed to send to player " + playerId + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }).start();
            });
        } else {
            logger.warning("[BATTLESHIP SERVER]: No connections found for game " + gameId);
        }
    }

    public void removePlayerFromGame(String gameId, int playerId) {
        logger.info("[BATTLESHIP SERVER]: === REMOVING PLAYER FROM GAME ===");
        logger.info("[BATTLESHIP SERVER]: Removing player " + playerId + " from game " + gameId);

        handlePlayerDisconnection(gameId, playerId);

        Map<Integer, BattleshipClientHandler> connections = gameConnections.get(gameId);
        if (connections != null) {
            connections.remove(playerId);


            if (connections.isEmpty()) {

                BattleshipGame game = activeGames.get(gameId);
                if (game != null && (game.getState() == GameState.PLAYING || game.getState() == GameState.PAUSED)) {
                    logger.info("[BATTLESHIP SERVER]: Keeping game " + gameId + " for potential rejoin");

                } else {

                    activeGames.remove(gameId);
                    gameConnections.remove(gameId);
                    logger.info("[BATTLESHIP SERVER]: Game " + gameId + " removed - no players left and not in progress");
                }
            }
        }
    }

    public void checkConnections() {
        logger.info("[BATTLESHIP SERVER]: === CONNECTION CHECK ===");

        for (Map.Entry<String, Map<Integer, BattleshipClientHandler>> gameEntry : gameConnections.entrySet()) {
            String gameId = gameEntry.getKey();
            Map<Integer, BattleshipClientHandler> connections = gameEntry.getValue();

            logger.info("[BATTLESHIP SERVER]: Checking " + connections.size() + " connections for game: " + gameId);

            connections.entrySet().removeIf(entry -> {
                int playerId = entry.getKey();
                BattleshipClientHandler handler = entry.getValue();

                try {
                    if (handler == null || !handler.isConnected()) {
                        logger.info("[BATTLESHIP SERVER]: Removing dead connection for player " + playerId + " in game " + gameId);
                        return true;
                    }

                    // Wyślij ping (game update) żeby sprawdzić połączenie
                    BattleshipGame game = activeGames.get(gameId);
                    if (game != null) {
                        handler.sendMessage(new GameUpdateMessage(game));
                        return false;
                    }

                } catch (Exception e) {
                    logger.info("[BATTLESHIP SERVER]: Exception checking player " + playerId + ": " + e.getMessage());
                    return true;
                }

                return false;
            });

            logger.info("[BATTLESHIP SERVER]: After cleanup: " + connections.size() + " connections remain for game " + gameId);
        }

        logger.info("[BATTLESHIP SERVER]: === CONNECTION CHECK COMPLETE ===");
    }

    public BattleshipGame getGame(String gameId) {
        return activeGames.get(gameId);
    }

    public void verifyConnections(String gameId) {
        Map<Integer, BattleshipClientHandler> connections = gameConnections.get(gameId);
        if (connections != null) {
            logger.info("[BATTLESHIP SERVER]: === VERIFYING CONNECTIONS ===");
            connections.forEach((playerId, handler) -> {
                boolean active = handler != null && handler.isRunning();
                logger.info("[BATTLESHIP SERVER]: Player " + playerId + " - handler active: " + active);
            });
        }
    }

    private BattleshipGame restoreOrCreateGame(String gameId) {

        if (pausedGameStates.containsKey(gameId)) {
            logger.info("[BATTLESHIP SERVER]: Restoring saved game state for: " + gameId);
            return pausedGameStates.get(gameId);
        }


        logger.info("[BATTLESHIP SERVER]: Creating new game: " + gameId);
        return new BattleshipGame(gameId);
    }

    private void handleGameRejoin(String gameId, int playerId, BattleshipClientHandler handler) {
        logger.info("[BATTLESHIP SERVER]: === GAME REJOIN ===");

        BattleshipGame game = pausedGameStates.get(gameId);
        if (game != null) {
            activeGames.put(gameId, game);

            Set<Integer> activePlayer = activePlayersInGame.get(gameId);
            Set<Integer> gamePlayers = game.getPlayerBoards().keySet();

            if (activePlayer != null && activePlayer.containsAll(gamePlayers)) {
                logger.info("[BATTLESHIP SERVER]: All players reconnected, resuming game");

                try {
                    gameService.resumeGame(gameId);
                    game.setState(GameState.PLAYING);
                    pausedGameStates.remove(gameId);
                } catch (SQLException e) {
                    logger.severe("[BATTLESHIP SERVER]: Error resuming game: " + e.getMessage());
                }
            }

            GameUpdateMessage updateMessage = new GameUpdateMessage(game);
            broadcastToGame(gameId, updateMessage);
        }
    }

    private void handlePlayerDisconnection(String gameId, int playerId) {
        logger.info("[BATTLESHIP SERVER]: === PLAYER DISCONNECTION ===");
        logger.info("[BATTLESHIP SERVER]: Player " + playerId + " disconnected from game " + gameId);

        Set<Integer> activePlayers = activePlayersInGame.get(gameId);
        if (activePlayers != null) {
            activePlayers.remove(playerId);

            BattleshipGame game = activeGames.get(gameId);
            if (game != null && game.getState() == GameState.PLAYING) {
                logger.info("[BATTLESHIP SERVER]: Pausing game due to player disconnection");
                pausedGameStates.put(gameId, game);
                try {
                    gameService.pauseGame(gameId);
                    game.setState(GameState.PAUSED); // Dodaj PAUSED do GameState enum
                } catch (SQLException e) {
                    logger.severe("[BATTLESHIP SERVER]: Error pausing game: " + e.getMessage());
                }
                broadcastToGame(gameId, new GameUpdateMessage(game));
            }
        }
    }

    private void sendPingToAllPlayers() {
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<String, Map<Integer, BattleshipClientHandler>> gameEntry : gameConnections.entrySet()) {
            String gameId = gameEntry.getKey();
            Map<Integer, BattleshipClientHandler> connections = gameEntry.getValue();

            for (Map.Entry<Integer, BattleshipClientHandler> playerEntry : connections.entrySet()) {
                int playerId = playerEntry.getKey();
                BattleshipClientHandler handler = playerEntry.getValue();

                if (handler != null && handler.isConnected()) {
                    BattleshipGame game = activeGames.get(gameId);
                    if (game != null) {
                        try {
                            handler.sendMessage(new GameUpdateMessage(game));

                            lastPingTimes.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>())
                                    .put(playerId, currentTime);

                        } catch (Exception e) {
                            logger.severe("[BATTLESHIP SERVER]: Failed to send ping to player " + playerId + ": " + e.getMessage());

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
                    logger.info("[BATTLESHIP SERVER]: Player " + playerId + " in game " + gameId + " timed out");
                    markPlayerAsDisconnected(gameId, playerId);
                }
            }
        }
    }

    private void markPlayerAsDisconnected(String gameId, int playerId) {
        logger.info("[BATTLESHIP SERVER]: === MARKING PLAYER AS DISCONNECTED ===");
        logger.info("[BATTLESHIP SERVER]: Player " + playerId + " in game " + gameId);

        Map<Integer, BattleshipClientHandler> connections = gameConnections.get(gameId);
        if (connections != null) {
            BattleshipClientHandler handler = connections.remove(playerId);
            if (handler != null) {
                try {
                    if (!handler.isConnected()) {
                        logger.info("[BATTLESHIP SERVER]: Handler already disconnected, closing gracefully");
                    }
                } catch (Exception e) {
                }
            }
        }

        handlePlayerDisconnection(gameId, playerId);

        Map<Integer, Long> gamePings = lastPingTimes.get(gameId);
        if (gamePings != null) {
            gamePings.remove(playerId);
            if (gamePings.isEmpty()) {
                lastPingTimes.remove(gameId);
            }
        }
    }

}