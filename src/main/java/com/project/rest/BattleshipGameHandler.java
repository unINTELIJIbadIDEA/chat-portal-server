package com.project.rest;

import com.google.gson.Gson;
import com.project.server.ApiServer;
import com.project.services.BattleshipGameService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
                        // GET /api/battleship/chat/{chatId} - do pobierania gry dla czatu
                        handleGetChatGames(exchange, userId, chatId);
                    } else if (pathParts.length == 4 && pathParts[3].equals("join") && exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                        // POST /api/battleship/chat/{chatId}/join - dołącz do gry w czacie
                        handleJoinChatGame(exchange, userId, chatId);
                    } else {
                        exchange.sendResponseHeaders(404, -1);
                    }
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            } else if (relativePath.startsWith("/") && exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                // GET /api/battleship/{gameId} - informacje o grze
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

    private void handleCreateGame(HttpExchange exchange, int userId) throws IOException {
        Map<String, String> request = gson.fromJson(new InputStreamReader(exchange.getRequestBody()), Map.class);
        String gameName = request.get("gameName");
        String chatId = request.get("chatId");

        if (chatId == null || chatId.trim().isEmpty()) {
            sendResponse(exchange, 400, "Chat ID is required");
            return;
        }

        try {
            // czy użytkownik należy do chatu
            if (!gameService.isUserInChat(userId, chatId)) {
                sendResponse(exchange, 403, "You are not a member of this chat");
                return;
            }

            String gameId = gameService.createGame(userId, gameName, chatId);
            if (gameId != null) {
                Map<String, Object> response = Map.of(
                        "gameId", gameId,
                        "status", "created",
                        "chatId", chatId,
                        "battleshipServerPort", gameService.getBattleshipServerPort()
                );
                sendResponse(exchange, 201, gson.toJson(response));
            } else {
                sendResponse(exchange, 500, "Failed to create game");
            }
        } catch (Exception e) {
            sendResponse(exchange, 500, "Failed to create game: " + e.getMessage());
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
            // czy użytkownik należy do czatu
            if (!gameService.isUserInChat(userId, chatId)) {
                sendResponse(exchange, 403, "You are not a member of this chat");
                return;
            }

            boolean joined = gameService.joinChatGame(userId, chatId);
            if (joined) {
                // pobieram informacje o grze do której dołączył
                var gameInfo = gameService.getActiveChatGame(chatId);
                if (gameInfo != null) {
                    Map<String, Object> response = Map.of(
                            "gameId", gameInfo.getGameId(),
                            "status", "joined",
                            "chatId", chatId,
                            "battleshipServerPort", gameService.getBattleshipServerPort(),
                            "playerId", userId
                    );
                    sendResponse(exchange, 200, gson.toJson(response));
                } else {
                    sendResponse(exchange, 500, "Game not found after joining");
                }
            } else {
                sendResponse(exchange, 409, "Cannot join game - no available games or game is full");
            }
        } catch (Exception e) {
            sendResponse(exchange, 500, "Failed to join game: " + e.getMessage());
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
}
