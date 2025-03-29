package com.project.apiServer;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class ApiServer {
    private static final int PORT = 8080;
    private static final String postPath = "/api/posts";
    private static final String userPath = "/api/users";

    public static void main(String[] args) throws InterruptedException {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext(postPath, new PostsHandler());
            server.createContext(userPath, new UsersHandler());
            server.setExecutor(null);
            server.start();
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return;
        }
    }
}

