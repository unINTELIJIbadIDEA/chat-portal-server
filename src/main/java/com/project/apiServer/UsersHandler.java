package com.project.apiServer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

public class UsersHandler implements HttpHandler {
    private static final String dbURL = "jdbc:mysql://localhost:3306/portal";
    private static final String dbUsername = "root";
    private static final String dbPassword = "";
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Post.class, new PostAdapter())
            .create();
    @Override
    public void handle(HttpExchange exchange) throws IOException {

    }
}
