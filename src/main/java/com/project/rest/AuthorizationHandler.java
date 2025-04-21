package com.project.rest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.project.services.AuthorizationService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.stream.Collectors;

public class AuthorizationHandler implements HttpHandler {

    private final Gson gson = new Gson();
    private final AuthorizationService authorizationService;

    public AuthorizationHandler() {
        this.authorizationService = new AuthorizationService();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            return;
        }

        String requestBody = readRequestBody(exchange);

        String email = null;
        String password = null;

        try {
            email = gson.fromJson(requestBody, JsonObject.class).get("email").getAsString();
            password = gson.fromJson(requestBody, JsonObject.class).get("password").getAsString();
        } catch (JsonParseException e) {
            sendResponse(exchange, 400, "Invalid JSON format");
            return;
        }

        try {
            String token = authorizationService.authenticateUser(email, password);
            if (token != null) {
                sendResponse(exchange, 200, "{ \"token\": \"Bearer " + token + "\" }");
            } else {
                sendResponse(exchange, 401, "Invalid email or password");
            }
        } catch (SQLException e) {
            sendResponse(exchange, 500, "Database error");
            System.out.println(e.getMessage());
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String responseContent) throws IOException {
        byte[] responseBytes = responseContent.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
