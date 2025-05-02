package com.project.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.project.adapters.UserAdapter;
import com.project.rest.AuthorizationHandler;
import com.project.rest.MessageHandler;
import com.project.utils.Config;
import com.project.rest.PostsHandler;
import com.project.rest.UsersHandler;
import com.project.models.Post;
import com.project.security.TokenManager;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class ApiServer {

    private static final String postPath = "/api/posts";
    private static final String userPath = "/api/users";
    private static final String messagePath = "/api/messages";
    private static final String authenticationPath = "/api/login";

    private static final int PORT = Config.getPORT_API();

    private HttpServer server;

    static final TokenManager tokenManager = new TokenManager(Config.getSecretKey(), Config.getExpirationTime());

    public static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Post.class, new UserAdapter())
            .create();

    public static final TokenManager getTokenManager() {
        return tokenManager;
    }

    public void runServer(){
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext(postPath, new PostsHandler());
            server.createContext(userPath, new UsersHandler());
            server.createContext(messagePath, new MessageHandler());
            server.createContext(authenticationPath, new AuthorizationHandler());
            server.setExecutor(null);
            server.start();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public void stopServer() {
        if (server != null) {
            System.out.println("[API SERVER]: Stopping...");
            server.stop(0);
        }
    }

    //niech pozostanie do testów
    @Deprecated
    public static void main(String[] args) throws InterruptedException {
        ApiServer server = new ApiServer();
        server.runServer();
    }
}

