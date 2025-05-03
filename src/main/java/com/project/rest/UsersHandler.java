package com.project.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.project.adapters.UserAdapter;
import com.project.models.User;
import com.project.services.UsersService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

public class UsersHandler implements HttpHandler {
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(User.class, new UserAdapter())
            .create();
    private final UsersService service = new UsersService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        String path = exchange.getHttpContext().getPath();
        String rel = exchange.getRequestURI().getPath().substring(path.length());

        try {
            if (rel.isEmpty() || rel.equals("/")) {
                switch (method) {
                    case "GET": handleGetAll(exchange); break;
                    case "POST": handleAdd(exchange); break;
                    default: exchange.sendResponseHeaders(405, -1);
                }
            } else {
                int id = Integer.parseInt(rel.substring(1));
                switch (method) {
                    case "GET": handleGetById(exchange, id); break;
                    case "PUT": handleUpdate(exchange, id); break;
                    case "DELETE": handleDelete(exchange, id); break;
                    default: exchange.sendResponseHeaders(405, -1);
                }
            }
        } catch (NumberFormatException e) {
            exchange.sendResponseHeaders(400, -1);
        }
    }

    private void handleGetAll(HttpExchange ex) throws IOException {
        try {
            List<User> users = service.getAllUsers();
            sendJson(ex, 200, gson.toJson(users));
        } catch (SQLException e) {
            sendJson(ex, 500, "{ \"error\": \"Server error\" }");
        }
    }

    private void handleGetById(HttpExchange ex, int id) throws IOException {
        try {
            User user = service.getUserById(id);
            if (user == null) ex.sendResponseHeaders(404, -1);
            else sendJson(ex, 200, gson.toJson(user));
        } catch (SQLException e) {
            sendJson(ex, 500, "{ \"error\": \"Server error\" }");
        }
    }

    private void handleAdd(HttpExchange ex) throws IOException {
        InputStreamReader isr = new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8);
        try {
            User user = gson.fromJson(isr, User.class);
            boolean ok = service.addUser(user);
            if (ok) sendJson(ex, 201, gson.toJson(user));
            else sendJson(ex, 400, "{ \"error\": \"Invalid data\" }");
        } catch (JsonParseException | SQLException e) {
            sendJson(ex, 400, "{ \"error\": \"Bad request\" }");
        }
    }

    private void handleUpdate(HttpExchange ex, int id) throws IOException {
        InputStreamReader isr = new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8);
        try {
            User user = gson.fromJson(isr, User.class);
            user.setId(id);
            boolean ok = service.updateUser(user);
            if (ok) sendJson(ex, 200, "{ \"message\": \"Updated\" }");
            else sendJson(ex, 400, "{ \"error\": \"Could not update\" }");
        } catch (JsonParseException | SQLException e) {
            sendJson(ex, 400, "{ \"error\": \"Bad request\" }");
        }
    }

    private void handleDelete(HttpExchange ex, int id) throws IOException {
        try {
            boolean ok = service.deleteUser(id);
            if (ok) sendJson(ex, 200, "{ \"message\": \"Deleted\" }");
            else ex.sendResponseHeaders(404, -1);
        } catch (SQLException e) {
            sendJson(ex, 500, "{ \"error\": \"Server error\" }");
        }
    }

    private void sendJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}