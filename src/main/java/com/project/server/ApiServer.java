package com.project.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.project.adapters.UserAdapter;
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

    private static final int PORT = Config.getPORT_API();

    //jeszcze nieużywane, jest bardzo dużo teorii żeby to wszystko ogarnać. Ale dam radę
    static final TokenManager tokenManager = new TokenManager(Config.getSecretKey(), Config.getExpirationTime());



    //do tego mam pytanie czemu do Post.class jest user adapter?
    public static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Post.class, new UserAdapter())
            .create();

    public static final TokenManager getTokenManager() {
        return tokenManager;
    }




    public static void main(String[] args) throws InterruptedException {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext(postPath, new PostsHandler());
            server.createContext(userPath, new UsersHandler());
            server.createContext(messagePath, new MessageHandler());
            server.setExecutor(null);
            server.start();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}

