package com.project.rest;

import com.google.gson.*;
import com.project.adapters.LocalDateTimeAdapter;
import com.project.models.Message;
import com.project.services.MessageService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class MessageHandler implements HttpHandler {

    Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();


    private final MessageService messageService = new MessageService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {



        String contextPath = exchange.getHttpContext().getPath();
        String fullPath = exchange.getRequestURI().getPath();
        String relativePath = fullPath.substring(contextPath.length());


        if (relativePath.isEmpty() || relativePath.equals("/")) {
            switch (exchange.getRequestMethod().toUpperCase()) {
                case "GET":
                    handleGetMessages(exchange);
                    break;
                case "POST":
                    handleAddMessage(exchange);
                    break;
                default:
                    exchange.sendResponseHeaders(405, -1);
                    break;
            }
        }
        else if (relativePath.startsWith("/") && relativePath.length() > 1) {
            String idStr = relativePath.substring(1);
            try {
                int id = Integer.parseInt(idStr);
                switch (exchange.getRequestMethod().toUpperCase()) {
                    case "GET":
                        handleGetMessageById(exchange, id);
                        break;
                    case "PUT":
                        handleUpdateMessage(exchange, id);
                        break;
                    case "DELETE":
                        handleDeleteMessage(exchange, id);
                        break;
                    default:
                        exchange.sendResponseHeaders(405, -1);
                        break;
                }
            } catch (NumberFormatException e) {
                exchange.sendResponseHeaders(400, -1);
            }
        } else {
            exchange.sendResponseHeaders(404, -1);
        }
    }


    private void handleGetMessages(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String chatId = null;
        if (query != null && query.startsWith("chatId=")) {
            chatId = query.substring("chatId=".length());
        }
        try {
            List<Message> messages;
            if (chatId != null) {
                messages = messageService.getMessagesByChatId(chatId);
            } else {
                messages = messageService.getAllMessages();
            }
            String response = gson.toJson(messages);
            sendResponse(exchange, 200, response);
        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "Error fetching messages");
        }
    }


    private void handleGetMessageById(HttpExchange exchange, int id) throws IOException {
        try {
            String message = messageService.getMessageById(id);
            if (message != null) {
                sendResponse(exchange, 200, message);
            } else {
                sendResponse(exchange, 404, "Message not found");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "Error fetching message");
        }
    }

    public void handleAddMessage(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");

        InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);

        try {
            Message message = gson.fromJson(reader, Message.class);

            if (message.content() == null || message.content().trim().isEmpty()) {
                String response = "{\"error\": \"Message content cannot be empty\"}";
                exchange.sendResponseHeaders(400, response.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            boolean added = messageService.addMessage(message);
            if (!added) {
                String response = "{\"error\": \"Failed to add message\"}";
                exchange.sendResponseHeaders(500, response.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            String response = gson.toJson(message);
            exchange.sendResponseHeaders(201, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }

        } catch (JsonParseException | SQLException e) {
            String response = "{\"error\": \"Invalid message format\"}";
            exchange.sendResponseHeaders(400, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private void handleUpdateMessage(HttpExchange exchange, int id) throws IOException {
        String requestBody = readRequestBody(exchange);
        Message receivedMessage = gson.fromJson(requestBody, Message.class);
        Message updatedMessage = new Message(
                id,
                receivedMessage.chat_id(),
                receivedMessage.sender_id(),
                receivedMessage.content(),
                receivedMessage.time()
        );
        try {
            boolean isUpdated = messageService.updateMessage(updatedMessage);
            if (isUpdated) {
                sendResponse(exchange, 200, "Message updated successfully");
            } else {
                sendResponse(exchange, 500, "Failed to update message");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "Error updating message");
        }
    }


    private void handleDeleteMessage(HttpExchange exchange, int id) throws IOException {
        try {
            boolean isDeleted = messageService.deleteMessage(id);
            if (isDeleted) {
                sendResponse(exchange, 200, "Message deleted successfully");
            } else {
                sendResponse(exchange, 500, "Failed to delete message");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "Error deleting message");
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
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
