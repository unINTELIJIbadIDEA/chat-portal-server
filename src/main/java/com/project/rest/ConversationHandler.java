package com.project.rest;

import com.google.gson.Gson;
import com.project.server.ApiServer;
import com.project.services.ConversationService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class ConversationHandler implements HttpHandler {
    private final Gson gson = new Gson();
    private final ConversationService service = new ConversationService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String contextPath = exchange.getHttpContext().getPath();
        String fullPath = exchange.getRequestURI().getPath();
        String relativePath = fullPath.substring(contextPath.length());

        Integer userId = authenticate(exchange);
        if (userId == null) return;

        try {
            if (relativePath.isEmpty() || relativePath.equals("/")) {
                switch (exchange.getRequestMethod().toUpperCase()) {
                    case "GET":
                        handleGetConversations(exchange, userId);
                        break;
                    case "POST":
                        handleCreateConversation(exchange, userId);
                        break;
                    default:
                        exchange.sendResponseHeaders(405, -1);
                        break;
                }
            } else if (relativePath.startsWith("/") && relativePath.endsWith("/join") && exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                String roomId = relativePath.split("/")[1];
                handleJoinConversation(exchange, userId, roomId);
            } else if (relativePath.startsWith("/") && relativePath.endsWith("/leave") && exchange.getRequestMethod().equalsIgnoreCase("DELETE")) {
                String roomId = relativePath.split("/")[1];
                handleLeaveConversation(exchange, userId, roomId);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "Internal Server Error");
        }
    }

    private void handleCreateConversation(HttpExchange exchange, int userId) throws IOException {
        Map<String, String> request = gson.fromJson(new InputStreamReader(exchange.getRequestBody()), Map.class);
        String roomId = request.get("roomId");
        String password = request.get("password");

        if (roomId == null || password == null) {
            sendResponse(exchange, 400, "Missing parameters");
            return;
        }

        try {
            if (service.createConversation(userId, roomId, password)) {
                sendResponse(exchange, 201, "Conversation created");
            } else {
                sendResponse(exchange, 409, "Conversation already exists");
            }
        } catch (SQLException e) {
            sendResponse(exchange, 500, "Database error");
        }
    }

    private void handleJoinConversation(HttpExchange exchange, int userId, String roomId) throws IOException {
        Map<String, String> request = gson.fromJson(new InputStreamReader(exchange.getRequestBody()), Map.class);
        String password = request.get("password");

        try {
            if (service.joinConversation(userId, roomId, password)) {
                sendResponse(exchange, 200, "Joined conversation");
            } else {
                sendResponse(exchange, 401, "Invalid password or room");
            }
        } catch (SQLException e) {
            sendResponse(exchange, 500, "Database error");
        }
    }

    private void handleLeaveConversation(HttpExchange exchange, int userId, String roomId) throws IOException {
        try {
            if (service.leaveConversation(userId, roomId)) {
                sendResponse(exchange, 200, "Left conversation");
            } else {
                sendResponse(exchange, 404, "Not in conversation");
            }
        } catch (SQLException e) {
            sendResponse(exchange, 500, "Database error");
        }
    }

    private void handleGetConversations(HttpExchange exchange, int userId) throws IOException {
        try {
            List<String> conversations = service.getUserConversations(userId);
            sendResponse(exchange, 200, gson.toJson(conversations));
        } catch (SQLException e) {
            sendResponse(exchange, 500, "Database error");
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
        exchange.sendResponseHeaders(code, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
}
