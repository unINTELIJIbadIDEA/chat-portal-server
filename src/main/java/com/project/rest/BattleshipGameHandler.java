package com.project.rest;

import com.google.gson.Gson;
import com.project.models.battleship.BattleshipGameInfo;
import com.project.server.ApiServer;
import com.project.services.BattleshipGameService;
import com.project.config.ConfigProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
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
                    } else if (pathParts.length == 4 && pathParts[3].equals("resume") && exchange.getRequestMethod().equalsIgnoreCase("POST")) {

                        handleResumeGame(exchange, userId, chatId);
                    } else {
                        exchange.sendResponseHeaders(404, -1);
                    }
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
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


            if (!gameService.isUserInChat(userId, chatId)) {
                System.out.println("[BATTLESHIP HANDLER]: User not in chat");
                sendResponse(exchange, 403, "{\"error\": \"You are not a member of this chat\"}");
                return;
            }

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


            System.out.println("[BATTLESHIP HANDLER]: Attempting to join game...");
            boolean joined = gameService.joinChatGame(userId, chatId);

            if (joined) {
                System.out.println("[BATTLESHIP HANDLER]: Successfully joined game");

                var gameInfo = gameService.getActiveChatGame(chatId);

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

            System.out.println("Checking chat membership...");
            if (!gameService.isUserInChat(userId, chatId)) {
                System.out.println("User not in chat");
                sendResponse(exchange, 403, "{\"error\": \"You are not a member of this chat\"}");
                return;
            }
            System.out.println("User is member of chat");

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

                try {
                    notifyChatAboutGame(chatId, response, userId);
                    System.out.println("Chat notification sent");
                } catch (Exception e) {
                    System.err.println("Failed to notify chat: " + e.getMessage());

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

    private void notifyChatAboutGame(String chatId, Map<String, Object> gameInfo, int creatorId) {
        try {

            java.net.Socket notificationSocket = new java.net.Socket("localhost", ConfigProperties.getLOCAL_SERVER_PORT());

            java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(notificationSocket.getOutputStream());
            out.flush();


            String token = ApiServer.getTokenManager().generateToken(String.valueOf(creatorId));
            com.project.models.message.ClientMessage joinMessage =
                    new com.project.models.message.ClientMessage("/join " + chatId, chatId, token);
            out.writeObject(joinMessage);
            out.flush();

            Thread.sleep(100);

            String gameData = gson.toJson(gameInfo);
            com.project.models.message.ClientMessage gameMessage =
                    new com.project.models.message.ClientMessage("/game_notification:" + gameData, chatId, token);
            out.writeObject(gameMessage);
            out.flush();

            // Zamknij połączenie
            out.close();
            notificationSocket.close();

            System.out.println("Game notification sent to chat: " + chatId);

        } catch (Exception e) {
            System.err.println("Failed to notify chat about game: " + e.getMessage());
            e.printStackTrace();
        }
    }



    private void handleResumeGame(HttpExchange exchange, int userId, String chatId) throws IOException {
        try {
            System.out.println("[BATTLESHIP HANDLER]: Resume game request - User: " + userId + ", Chat: " + chatId);

            // Sprawdź czy użytkownik należy do czatu
            if (!gameService.isUserInChat(userId, chatId)) {
                sendResponse(exchange, 403, "{\"error\": \"You are not a member of this chat\"}");
                return;
            }

            List<BattleshipGameInfo> pausedGames = gameService.getPausedGamesForUser(userId);
            BattleshipGameInfo gameToResume = pausedGames.stream()
                    .filter(game -> game.getChatId().equals(chatId))
                    .findFirst()
                    .orElse(null);

            if (gameToResume == null) {
                sendResponse(exchange, 404, "{\"error\": \"No paused game found in this chat\"}");
                return;
            }

            // Wznów grę
            boolean resumed = gameService.resumeGame(gameToResume.getGameId());

            if (resumed) {
                Map<String, Object> response = Map.of(
                        "gameId", gameToResume.getGameId(),
                        "status", "PLAYING",
                        "chatId", chatId,
                        "gameName", gameToResume.getGameName(),
                        "battleshipServerPort", gameService.getBattleshipServerPort(),
                        "message", "Game resumed successfully",
                        "action", "resumed"
                );
                sendResponse(exchange, 200, gson.toJson(response));
            } else {
                sendResponse(exchange, 500, "{\"error\": \"Failed to resume game\"}");
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\": \"Internal server error: " + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

}
