package com.project.rest;

import com.google.gson.JsonObject;
import com.project.config.ConfigDAO;
import com.project.dao.IPostsDAO;
import com.project.models.Post;
import com.project.server.ApiServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.project.server.ApiServer.*;

public class PostsHandler implements HttpHandler {
    private IPostsDAO dao;

    public PostsHandler() {
        this.dao = ConfigDAO.getInstance().getPostsDAO();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        System.out.println("Hanlduje request");
        try {
            if (method.equalsIgnoreCase("GET")) {
                handleGetRequest(exchange);
            } else if (method.equalsIgnoreCase("POST")) {
                handlePostRequest(exchange);
            }else if (method.equalsIgnoreCase("PUT")) {
                handlePutRequest(exchange);
            } else if(method.equalsIgnoreCase("DELETE")) {
                handleDeleteRequest(exchange);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        } catch (IOException | SQLException | InterruptedException e) {
            System.out.println("Error przy handlingu requesta");
            System.out.println(e.getMessage());
            exchange.sendResponseHeaders(404, -1);
        } catch (Exception e) {
            System.out.println("Niestandardowy błąd");
            System.out.println(e.getMessage());
            e.printStackTrace();
            exchange.sendResponseHeaders(404, -1);
        }
    }

    private void handlePutRequest(HttpExchange exchange) throws SQLException, IOException {
        String path = exchange.getRequestURI().getPath();
        String postIdStr = path.substring(path.lastIndexOf("/") + 1);

        if (postIdStr.isEmpty()) {
            sendResponse(exchange, 400, "Brak ID posta w ścieżce");
            return;
        }

        int postId;
        try {
            postId = Integer.parseInt(postIdStr);
        } catch (NumberFormatException e) {
            sendResponse(exchange, 400, "Nieprawidłowe ID posta");
            return;
        }

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendResponse(exchange, 401, "Brak lub nieprawidłowy nagłówek Authorization");
            return;
        }

        String token = authHeader.substring("Bearer ".length());
        Integer userId = ApiServer.getTokenManager().validateTokenAndGetUserId(token);
        if (userId == null) {
            sendResponse(exchange, 401, "Nieprawidłowy token");
            return;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
        JsonObject json = gson.fromJson(reader, JsonObject.class);
        String newContent = json.get("content").getAsString();


        dao.connect();

        boolean isUpdated = dao.updatePost(postId, newContent);
        if (isUpdated) {
            sendResponse(exchange, 200, "Post zaktualizowany pomyślnie");
        } else {
            sendResponse(exchange, 500, "Nie udało się zaktualizować posta");
        }

        dao.close();
    }

    private void handleDeleteRequest(HttpExchange exchange) throws SQLException, IOException {
        String path = exchange.getRequestURI().getPath();
        String postIdStr = path.substring(path.lastIndexOf("/") + 1);
        String token = exchange.getRequestHeaders().getFirst("Authorization");

        if (token == null || !token.startsWith("Bearer ")) {
            sendResponse(exchange, 401, "Brak lub nieprawidłowy nagłówek Authorization");
            return;
        }

        Integer userId = ApiServer.getTokenManager().validateTokenAndGetUserId(token.substring("Bearer ".length()));

        if (userId == null) {
            sendResponse(exchange, 401, "Nieprawidłowy token");
            return;
        }

        dao.connect();
        boolean isDeleted = dao.deletePostWithId(Integer.parseInt(postIdStr));

        if (!isDeleted) {
            throw new SQLException("Nie udało się usunąć");
        }
        sendResponse(exchange, 200, "Post Deleted Successfully");
    }

    private void handlePostRequest(HttpExchange exchange) throws SQLException, IOException {
        InputStreamReader inputStreamReader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        JsonObject jsonObject = gson.fromJson(bufferedReader, JsonObject.class);
        String token = jsonObject.get("token").getAsString();
        String content = jsonObject.get("content").getAsString();
        Integer userId = ApiServer.getTokenManager().validateTokenAndGetUserId(token);

        if(userId == null) {
            sendResponse(exchange, 401, "INVALID TOKEN");
            return;
        }


        Post newPost = new Post(
                -1,
                userId,
                content,
                LocalDate.now().toString()
        );

        dao.connect();

        boolean isAdded = dao.addPost(newPost);

        if (isAdded) {
            sendResponse(exchange, 200, "Post added successfully");
        } else {
            sendResponse(exchange, 500, "Failed to add post");
        }

    }

    private void handleGetRequest(HttpExchange exchange) throws IOException, SQLException, InterruptedException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if(authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendResponse(exchange, 401, "Brak lub nieprawidłowy nagłówek Authorization");
            return;
        }

        String token = authHeader.substring("Bearer ".length());
        Integer userId = ApiServer.getTokenManager().validateTokenAndGetUserId(token);

        if(userId == null) {
            sendResponse(exchange, 401, "Invalid token");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if(path.endsWith("exclude")) {
            getPostsExcludeId(exchange, userId);
        } else {
            getPostsWithUserId(exchange, userId);
        }
    }

    private void getPostsExcludeId(HttpExchange exchange, int userId) throws IOException, SQLException {
        dao.connect();

        String responseContent = dao.getAllPostsExcludingId(userId);
        sendResponse(exchange, 200, responseContent);
        dao.close();
    }

    private void getPostsWithUserId(HttpExchange exchange, int userId) throws SQLException, IOException, InterruptedException {
        dao.connect();

        String responseContent = dao.getAllPostsWithUserId(userId);
        sendResponse(exchange, 200, responseContent);
        dao.close();
    }

    private Map<String, String> getQueryParams(String query) {
        if (query == null) {
            return Map.of();
        }

        return Stream.of(query.split("&"))
                .map(p -> p.split("="))
                .collect(Collectors.toMap(pp -> pp[0], pp -> pp.length > 1 ? pp[1] : ""));
    }

    private void getAllPosts(HttpExchange exchange) throws SQLException, IOException {
        dao.connect();
        String responseContent = dao.getAllPosts();

        sendResponse(exchange, 200, responseContent);
        dao.close();
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String responseContent) throws IOException {
        exchange.sendResponseHeaders(statusCode, responseContent.getBytes(StandardCharsets.UTF_8).length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseContent.getBytes());
        os.close();
    }
}

