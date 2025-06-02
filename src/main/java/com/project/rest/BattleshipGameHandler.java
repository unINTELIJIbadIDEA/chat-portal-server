package com.project.rest;

import com.google.gson.Gson;
import com.project.models.battleship.BattleshipGameInfo;
import com.project.server.ApiServer;
import com.project.services.BattleshipGameService;
import com.project.utils.Config;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;

public class BattleshipGameHandler implements HttpHandler {
    private final Gson gson = new Gson();
    private final BattleshipGameService gameService = new BattleshipGameService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String contextPath = exchange.getHttpContext().getPath();
        String fullPath = exchange.getRequestURI().getPath();
        String relativePath = fullPath.substring(contextPath.length());

        Integer userId = authenticate(exchange);
        if (userId == null) return;

        try {
            if (relativePath.isEmpty() || relativePath.equals("/")) {
                // Tylko POST - tworzenie gry
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleCreateGame(exchange, userId);
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
            } else if (relativePath.startsWith("/chat/")) {
                String[] pathParts = relativePath.split("/");
                if (pathParts.length >= 3) {
                    String chatId = pathParts[2];

                    if (pathParts.length == 3 && exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                        handleGetChatGames(exchange, userId, chatId);
                    } else if (pathParts.length == 4 && pathParts[3].equals("join") && exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                        handleJoinChatGame(exchange, userId, chatId);
                    } else {
                        exchange.sendResponseHeaders(404, -1);
                    }
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            } else if (relativePath.startsWith("/") && relativePath.contains("/resume") && exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                // NOWY ENDPOINT - wznawianie gry
                String gameId = relativePath.split("/")[1];
                handleResumeGame(exchange, userId, gameId);
            } else if (relativePath.startsWith("/") && exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                String gameId = relativePath.substring(1);
                handleGetGameInfo(exchange, userId, gameId);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "Internal Server Error");
        }
    }

    private void handleGetChatGames(HttpExchange exchange, int userId, String chatId) throws IOException {
        try {
            // czy użytkownik należy do chatu
            if (!gameService.isUserInChat(userId, chatId)) {
                sendResponse(exchange, 403, "You are not a member of this chat");
                return;
            }

            var games = gameService.getChatGames(chatId);
            sendResponse(exchange, 200, gson.toJson(games));
        } catch (Exception e) {
            sendResponse(exchange, 500, "Failed to get chat games: " + e.getMessage());
        }
    }

    private void handleJoinChatGame(HttpExchange exchange, int userId, String chatId) throws IOException {
        try {
            System.out.println("[BATTLESHIP HANDLER]: Join request - User: " + userId + ", Chat: " + chatId);

            // Sprawdź czy użytkownik należy do czatu
            if (!gameService.isUserInChat(userId, chatId)) {
                System.out.println("[BATTLESHIP HANDLER]: User not in chat");
                sendResponse(exchange, 403, "{\"error\": \"You are not a member of this chat\"}");
                return;
            }

            // Sprawdź czy użytkownik już ma grę w tym czacie
            BattleshipGameInfo existingGame = gameService.getUserActiveGameInChat(userId, chatId);
            if (existingGame != null) {
                System.out.println("[BATTLESHIP HANDLER]: User already has game: " + existingGame.getGameId());
                Map<String, Object> response = Map.of(
                        "gameId", existingGame.getGameId(),
                        "status", existingGame.getStatus(),
                        "chatId", chatId,
                        "gameName", existingGame.getGameName(),
                        "battleshipServerPort", gameService.getBattleshipServerPort(),
                        "playerId", userId,
                        "message", "You are already in this game",
                        "action", "rejoin"
                );
                sendResponse(exchange, 200, gson.toJson(response));
                return;
            }

            // Próbuj dołączyć do gry
            System.out.println("[BATTLESHIP HANDLER]: Attempting to join game...");
            boolean joined = gameService.joinChatGame(userId, chatId);

            if (joined) {
                System.out.println("[BATTLESHIP HANDLER]: Successfully joined game");
                // Pobierz informacje o grze do której dołączył - sprawdź wszystkie aktywne gry
                var gameInfo = gameService.getActiveChatGame(chatId);

                // Jeśli nie ma aktywnej gry, sprawdź czy użytkownik ma jakąkolwiek grę w tym czacie
                if (gameInfo == null) {
                    System.out.println("[BATTLESHIP HANDLER]: No active chat game found, checking user games...");
                    gameInfo = gameService.getUserActiveGameInChat(userId, chatId);
                }

                if (gameInfo != null) {
                    System.out.println("[BATTLESHIP HANDLER]: Found game info: " + gameInfo.getGameId() + " with status: " + gameInfo.getStatus());
                    Map<String, Object> response = Map.of(
                            "gameId", gameInfo.getGameId(),
                            "status", gameInfo.getStatus(),
                            "chatId", chatId,
                            "gameName", gameInfo.getGameName(),
                            "battleshipServerPort", gameService.getBattleshipServerPort(),
                            "playerId", userId,
                            "action", "joined"
                    );
                    sendResponse(exchange, 200, gson.toJson(response));
                } else {
                    System.err.println("[BATTLESHIP HANDLER]: Game not found after joining - this should not happen");
                    sendResponse(exchange, 500, "{\"error\": \"Game not found after joining\"}");
                }
            } else {
                System.out.println("[BATTLESHIP HANDLER]: Failed to join game");
                sendResponse(exchange, 409, "{\"error\": \"Cannot join game - no available games, game is full, or you're trying to join your own game\"}");
            }

        } catch (SQLException e) {
            System.err.println("[BATTLESHIP HANDLER]: Database error: " + e.getMessage());
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Database error: " + e.getMessage().replace("\"", "'") + "\"}");
        } catch (Exception e) {
            System.err.println("[BATTLESHIP HANDLER]: Unexpected error: " + e.getMessage());
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Internal server error: " + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private void handleGetGameInfo(HttpExchange exchange, int userId, String gameId) throws IOException {
        try {
            Map<String, Object> gameInfo = gameService.getGameInfo(gameId, userId);
            if (gameInfo != null) {
                sendResponse(exchange, 200, gson.toJson(gameInfo));
            } else {
                sendResponse(exchange, 404, "Game not found or access denied");
            }
        } catch (Exception e) {
            sendResponse(exchange, 500, "Failed to get game info: " + e.getMessage());
        }
    }

    private Integer authenticate(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendResponse(exchange, 401, "Unauthorized");
            return null;
        }
        String token = authHeader.substring(7);
        Integer userId = ApiServer.getTokenManager().validateTokenAndGetUserId(token);
        if (userId == null) sendResponse(exchange, 401, "Invalid token");
        return userId;
    }

    private void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private void handleCreateGame(HttpExchange exchange, int userId) throws IOException {
        System.out.println("=== CREATE GAME REQUEST ===");
        System.out.println("User ID: " + userId);

        Map<String, String> request;
        try {
            request = gson.fromJson(new InputStreamReader(exchange.getRequestBody()), Map.class);
        } catch (Exception e) {
            System.err.println("Error parsing request: " + e.getMessage());
            sendResponse(exchange, 400, "{\"error\": \"Invalid JSON request\"}");
            return;
        }

        String gameName = request.get("gameName");
        String chatId = request.get("chatId");

        System.out.println("Game name: " + gameName);
        System.out.println("Chat ID: " + chatId);

        if (chatId == null || chatId.trim().isEmpty()) {
            sendResponse(exchange, 400, "{\"error\": \"Chat ID is required\"}");
            return;
        }

        try {
            // === SPRAWDŹ CZŁONKOSTWO W CZACIE ===
            System.out.println("Checking chat membership...");
            if (!gameService.isUserInChat(userId, chatId)) {
                System.out.println("User not in chat");
                sendResponse(exchange, 403, "{\"error\": \"You are not a member of this chat\"}");
                return;
            }
            System.out.println("User is member of chat");

            // === SPRAWDŹ ISTNIEJĄCE GRY ===
            System.out.println("Checking for existing games...");
            BattleshipGameInfo existingGame = gameService.getUserActiveGameInChat(userId, chatId);
            if (existingGame != null) {
                System.out.println("Found existing game: " + existingGame.getGameId());
                Map<String, Object> response = Map.of(
                        "gameId", existingGame.getGameId(),
                        "status", existingGame.getStatus(),
                        "chatId", chatId,
                        "gameName", existingGame.getGameName(),
                        "battleshipServerPort", gameService.getBattleshipServerPort(),
                        "message", "You already have an active game in this chat",
                        "action", "rejoin"
                );
                sendResponse(exchange, 200, gson.toJson(response));
                return;
            }

            // === UTWÓRZ NOWĄ GRĘ ===
            System.out.println("Creating new game...");
            String gameId = gameService.createGame(userId, gameName, chatId);
            if (gameId != null) {
                System.out.println("Game created successfully: " + gameId);

                Map<String, Object> response = Map.of(
                        "gameId", gameId,
                        "status", "WAITING",
                        "chatId", chatId,
                        "creatorId", userId,
                        "gameName", gameName,
                        "battleshipServerPort", gameService.getBattleshipServerPort(),
                        "action", "created"
                );

                // Powiadom czat przez TCP serwer
                try {
                    notifyChatAboutGame(chatId, response, userId);
                    System.out.println("Chat notification sent");
                } catch (Exception e) {
                    System.err.println("Failed to notify chat: " + e.getMessage());
                    // Nie przerywaj procesu - gra została utworzona
                }

                sendResponse(exchange, 201, gson.toJson(response));
            } else {
                System.err.println("Failed to create game - service returned null");
                sendResponse(exchange, 500, "{\"error\": \"Failed to create game - unknown error\"}");
            }

        } catch (SQLException e) {
            System.err.println("=== DATABASE ERROR IN HANDLER ===");
            System.err.println("Error: " + e.getMessage());
            System.err.println("SQLState: " + e.getSQLState());
            System.err.println("Error Code: " + e.getErrorCode());
            e.printStackTrace();

            String errorMsg = "Database error: " + e.getMessage();
            sendResponse(exchange, 500, "{\"error\": \"" + errorMsg.replace("\"", "'") + "\"}");

        } catch (Exception e) {
            System.err.println("=== UNEXPECTED ERROR IN HANDLER ===");
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Failed to create game: " + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    // NOWA METODA - powiadomienie przez połączenie TCP
    private void notifyChatAboutGame(String chatId, Map<String, Object> gameInfo, int creatorId) {
        try {
            // Pobierz nazwę twórcy gry
            BattleshipGameService gameService = new BattleshipGameService();
            String creatorName = gameService.getUserNickname(creatorId);

            // Wyślij przez wewnętrzne połączenie TCP
            java.net.Socket notificationSocket = new java.net.Socket("localhost", Config.getLOCAL_SERVER_PORT());

            java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(notificationSocket.getOutputStream());
            out.flush();

            // Wiadomość dołączenia do pokoju
            String token = ApiServer.getTokenManager().generateToken(String.valueOf(creatorId));
            com.project.models.message.ClientMessage joinMessage =
                    new com.project.models.message.ClientMessage("/join " + chatId, chatId, token);
            out.writeObject(joinMessage);
            out.flush();

            Thread.sleep(100);

            // Dodaj informację o twórcy do gameInfo
            gameInfo.put("creatorName", creatorName);

            // Wiadomość z informacją o grze
            String gameData = gson.toJson(gameInfo);
            com.project.models.message.ClientMessage gameMessage =
                    new com.project.models.message.ClientMessage("/game_notification:" + gameData, chatId, token);
            out.writeObject(gameMessage);
            out.flush();

            // Również wyślij zwykłą wiadomość informacyjną
            String inviteMessage = "🚢 " + creatorName + " utworzył grę w statki: " + gameInfo.get("gameName");
            com.project.models.message.ClientMessage inviteInfo =
                    new com.project.models.message.ClientMessage(inviteMessage, chatId, token);
            out.writeObject(inviteInfo);
            out.flush();

            out.close();
            notificationSocket.close();

            System.out.println("Game notification sent to chat: " + chatId);

        } catch (Exception e) {
            System.err.println("Failed to notify chat about game: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleResumeGame(HttpExchange exchange, int userId, String gameId) throws IOException {
        try {
            System.out.println("[BATTLESHIP HANDLER]: Resume game request - User: " + userId + ", Game: " + gameId);

            // Sprawdź czy użytkownik należy do tej gry
            BattleshipGameInfo gameInfo = gameService.getGameInfoDirect(gameId);
            if (gameInfo == null) {
                sendResponse(exchange, 404, "{\"error\": \"Game not found\"}");
                return;
            }

            // Sprawdź czy użytkownik jest graczem w tej grze
            boolean isPlayer = (gameInfo.getPlayer1Id() == userId) ||
                    (gameInfo.getPlayer2Id() != null && gameInfo.getPlayer2Id() == userId);

            if (!isPlayer) {
                sendResponse(exchange, 403, "{\"error\": \"You are not a player in this game\"}");
                return;
            }

            // Sprawdź czy gra jest w stanie PAUSED
            if (!"PAUSED".equals(gameInfo.getStatus())) {
                sendResponse(exchange, 400, "{\"error\": \"Game is not paused\"}");
                return;
            }

            // Wznów grę
            boolean resumed = gameService.resumeGame(gameId);
            if (resumed) {
                // Powiadom czat o wznowieniu
                notifyChatAboutGameResume(gameInfo, userId);

                Map<String, Object> response = Map.of(
                        "gameId", gameInfo.getGameId(),
                        "status", "READY", // Lub "PLAYING" jeśli gra była w trakcie
                        "chatId", gameInfo.getChatId(),
                        "gameName", gameInfo.getGameName(),
                        "battleshipServerPort", gameService.getBattleshipServerPort(),
                        "playerId", userId,
                        "action", "resumed"
                );
                sendResponse(exchange, 200, gson.toJson(response));
            } else {
                sendResponse(exchange, 500, "{\"error\": \"Failed to resume game\"}");
            }

        } catch (Exception e) {
            System.err.println("[BATTLESHIP HANDLER]: Error resuming game: " + e.getMessage());
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Internal server error\"}");
        }
    }

    private void notifyChatAboutGameResume(BattleshipGameInfo gameInfo, int userId) {
        try {
            String playerName = gameService.getUserNickname(userId);

            java.net.Socket notificationSocket = new java.net.Socket("localhost", Config.getLOCAL_SERVER_PORT());
            java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(notificationSocket.getOutputStream());
            out.flush();

            String token = ApiServer.getTokenManager().generateToken(String.valueOf(userId));
            com.project.models.message.ClientMessage joinMessage =
                    new com.project.models.message.ClientMessage("/join " + gameInfo.getChatId(), gameInfo.getChatId(), token);
            out.writeObject(joinMessage);
            out.flush();

            Thread.sleep(100);

            String resumeMessage = "▶️ " + playerName + " wznowił grę w statki: " + gameInfo.getGameName();
            com.project.models.message.ClientMessage notification =
                    new com.project.models.message.ClientMessage(resumeMessage, gameInfo.getChatId(), token);
            out.writeObject(notification);
            out.flush();

            out.close();
            notificationSocket.close();

            System.out.println("[BATTLESHIP HANDLER]: Game resume notification sent to chat: " + gameInfo.getChatId());

        } catch (Exception e) {
            System.err.println("[BATTLESHIP HANDLER]: Failed to notify about game resume: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
