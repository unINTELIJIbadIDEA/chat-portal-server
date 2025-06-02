package com.project.server;

import com.project.models.battleship.*;
import com.project.models.battleship.messages.*;
import com.project.services.BattleshipGameService;
import com.project.utils.Config;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
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
    private final Map<String, Set<Integer>> activePlayerConnections = new ConcurrentHashMap<>();

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

        // Dodaj gracza do aktywnych połączeń
        activePlayerConnections.computeIfAbsent(gameId, k -> ConcurrentHashMap.newKeySet()).add(playerId);

        // Sprawdź czy gra była wstrzymana i można ją wznowić
        try {
            BattleshipGameInfo gameInfo = gameService.getGameInfoDirect(gameId);
            if (gameInfo != null && "PAUSED".equals(gameInfo.getStatus())) {
                // Sprawdź czy obaj gracze są teraz online
                Set<Integer> activePlayers = activePlayerConnections.get(gameId);
                boolean bothPlayersOnline = activePlayers.contains(gameInfo.getPlayer1Id()) &&
                        (gameInfo.getPlayer2Id() != null && activePlayers.contains(gameInfo.getPlayer2Id()));

                if (bothPlayersOnline) {
                    // Automatycznie wznów grę
                    gameService.resumeGame(gameId);
                    System.out.println("[BATTLESHIP SERVER]: Game " + gameId + " automatically resumed - both players reconnected");

                    // Powiadom czat
                    notifyGameAutoResumed(gameId);
                }
            }
        } catch (Exception e) {
            System.err.println("[BATTLESHIP SERVER]: Error checking game status: " + e.getMessage());
        }

        // Reszta istniejącego kodu...
        Map<Integer, BattleshipClientHandler> connections = gameConnections.computeIfAbsent(
                gameId, k -> new ConcurrentHashMap<>()
        );

        connections.put(playerId, handler);
        System.out.println("[BATTLESHIP SERVER]: Player " + playerId + " connected to game " + gameId);
        System.out.println("[BATTLESHIP SERVER]: Total connections for game: " + connections.size());

        BattleshipGame game = activeGames.computeIfAbsent(
                gameId, k -> new BattleshipGame(gameId)
        );

        boolean added = game.addPlayer(playerId);
        System.out.println("[BATTLESHIP SERVER]: Player " + playerId +
                (added ? " successfully added" : " already in game or game full") + " to game " + gameId);

        System.out.println("[BATTLESHIP SERVER]: Current game state: " + game.getState());
        System.out.println("[BATTLESHIP SERVER]: Players in game: " + game.getPlayerBoards().keySet());
        System.out.println("[BATTLESHIP SERVER]: Players ready: " + game.getPlayersReady());

        GameUpdateMessage updateMessage = new GameUpdateMessage(game);
        broadcastToGame(gameId, updateMessage);

        if (game.getState() == GameState.PLAYING) {
            System.out.println("[BATTLESHIP SERVER]: Player rejoining game in PLAYING state");
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                    broadcastToGame(gameId, updateMessage);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        } else if (game.getState() == GameState.SHIP_PLACEMENT) {
            broadcastGameStateChange(gameId, game.getState());
        }

        System.out.println("[BATTLESHIP SERVER]: === CONNECTION HANDLING COMPLETE ===");
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

            // Pobierz nazwę zwycięzcy
            String winnerName = gameService.getUserNickname(winnerId);

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

            // Wiadomość o zakończeniu gry z nazwą gracza
            String gameEndMessage = "🎉 Gra w statki '" + gameInfo.getGameName() + "' zakończona! Wygrał: " + winnerName + " 🏆";
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
        // Usuń z aktywnych połączeń
        Set<Integer> activePlayers = activePlayerConnections.get(gameId);
        if (activePlayers != null) {
            activePlayers.remove(playerId);

            // Sprawdź czy gra powinna być wstrzymana
            try {
                BattleshipGameInfo gameInfo = gameService.getGameInfoDirect(gameId);
                if (gameInfo != null && ("PLAYING".equals(gameInfo.getStatus()) || "READY".equals(gameInfo.getStatus()))) {
                    // Jeśli jeden z graczy wyszedł, wstrzymaj grę
                    boolean player1Online = activePlayers.contains(gameInfo.getPlayer1Id());
                    boolean player2Online = gameInfo.getPlayer2Id() != null && activePlayers.contains(gameInfo.getPlayer2Id());

                    if (!player1Online || !player2Online) {
                        gameService.pauseGame(gameId);
                        System.out.println("[BATTLESHIP SERVER]: Game " + gameId + " paused due to player disconnect");
                        notifyPlayerLeft(gameId, playerId);
                    }
                }
            } catch (Exception e) {
                System.err.println("[BATTLESHIP SERVER]: Error checking game status for pause: " + e.getMessage());
            }
        }

        // Usuń gracza z połączeń do gry
        Map<Integer, BattleshipClientHandler> connections = gameConnections.get(gameId);
        if (connections != null) {
            connections.remove(playerId);

            // Jeśli nie ma już żadnych połączeń, usuń całą grę z pamięci
            if (connections.isEmpty()) {
                activeGames.remove(gameId);
                gameConnections.remove(gameId);
                activePlayerConnections.remove(gameId);
                System.out.println("[BATTLESHIP SERVER]: Game " + gameId + " removed from memory - no players left");
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

    public void notifyPlayerLeft(String gameId, int playerId) {
        try {
            BattleshipGameInfo gameInfo = gameService.getGameInfoDirect(gameId);
            if (gameInfo == null) return;

            String chatId = gameInfo.getChatId();
            String playerName = gameService.getUserNickname(playerId);

            // Wyślij powiadomienie o opuszczeniu gry
            java.net.Socket notificationSocket = new java.net.Socket("localhost", Config.getLOCAL_SERVER_PORT());
            java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(notificationSocket.getOutputStream());
            out.flush();

            // Użyj ID pozostałego gracza jako nadawcy
            int remainingPlayerId = (gameInfo.getPlayer1Id() == playerId) ?
                    (gameInfo.getPlayer2Id() != null ? gameInfo.getPlayer2Id() : playerId) :
                    gameInfo.getPlayer1Id();

            String token = ApiServer.getTokenManager().generateToken(String.valueOf(remainingPlayerId));
            com.project.models.message.ClientMessage joinMessage =
                    new com.project.models.message.ClientMessage("/join " + chatId, chatId, token);
            out.writeObject(joinMessage);
            out.flush();

            Thread.sleep(100);

            String leaveMessage = "⏸️ Gracz " + playerName + " opuścił grę w statki. Gra została wstrzymana.";
            com.project.models.message.ClientMessage notification =
                    new com.project.models.message.ClientMessage(leaveMessage, chatId, token);
            out.writeObject(notification);
            out.flush();

            out.close();
            notificationSocket.close();

            System.out.println("[BATTLESHIP SERVER]: Player leave notification sent to chat: " + chatId);

        } catch (Exception e) {
            System.err.println("[BATTLESHIP SERVER]: Failed to notify about player leaving: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void notifyGameAutoResumed(String gameId) {
        try {
            BattleshipGameInfo gameInfo = gameService.getGameInfoDirect(gameId);
            if (gameInfo == null) return;

            String chatId = gameInfo.getChatId();

            java.net.Socket notificationSocket = new java.net.Socket("localhost", Config.getLOCAL_SERVER_PORT());
            java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(notificationSocket.getOutputStream());
            out.flush();

            String token = ApiServer.getTokenManager().generateToken(String.valueOf(gameInfo.getPlayer1Id()));
            com.project.models.message.ClientMessage joinMessage =
                    new com.project.models.message.ClientMessage("/join " + chatId, chatId, token);
            out.writeObject(joinMessage);
            out.flush();

            Thread.sleep(100);

            String resumeMessage = "🔄 Gra w statki '" + gameInfo.getGameName() + "' została automatycznie wznowiona - obaj gracze powrócili!";
            com.project.models.message.ClientMessage notification =
                    new com.project.models.message.ClientMessage(resumeMessage, chatId, token);
            out.writeObject(notification);
            out.flush();

            out.close();
            notificationSocket.close();

            System.out.println("[BATTLESHIP SERVER]: Auto-resume notification sent to chat: " + chatId);

        } catch (Exception e) {
            System.err.println("[BATTLESHIP SERVER]: Failed to notify about auto-resume: " + e.getMessage());
            e.printStackTrace();
        }
    }

}