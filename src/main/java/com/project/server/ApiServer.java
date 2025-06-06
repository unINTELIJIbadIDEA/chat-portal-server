package com.project.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.project.adapters.UserAdapter;
import com.project.rest.*;
import com.project.config.ConfigProperties;
import com.project.models.UsersPost;
import com.project.security.TokenManager;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.*;

public class ApiServer {

        private static final Logger logger = Logger.getLogger(ApiServer.class.getName());

        static {
            try {
                FileHandler fileHandler = new FileHandler("logs/apiserver.log", true);
                fileHandler.setFormatter(new SimpleFormatter());
                logger.addHandler(fileHandler);
                logger.setLevel(Level.INFO);
            } catch (IOException e) {
                System.err.println("Failed to initialize API logger: " + e.getMessage());
            }
        }
    private static final String postPath = "/api/posts";
    private static final String userPath = "/api/users";
    private static final String messagePath = "/api/messages";
    private static final String conversationPath = "/api/conversations";
    private static final String authenticationPath = "/api/login";
    private static final String battleshipPath = "/api/battleship";

    private static final int PORT = ConfigProperties.getLOCAL_API_PORT();

    private HttpServer server;

    static final TokenManager tokenManager = new TokenManager(ConfigProperties.getSecretKey(), ConfigProperties.getExpirationTime());

    public static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(UsersPost.class, new UserAdapter())
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
                server.createContext(conversationPath, new ConversationHandler());
                server.createContext(authenticationPath, new AuthorizationHandler());
                server.createContext(battleshipPath, new BattleshipGameHandler());
                server.setExecutor(null);
                server.start();
                logger.info("[API SERVER]: Server started on port " + PORT);
            } catch (IOException e) {
                logger.severe("[API SERVER]: Failed to start: " + e.getMessage());
            }
        }

        public void stopServer() {
            if (server != null) {
                logger.info("[API SERVER]: Stopping...");
                server.stop(0);
                logger.info("[API SERVER]: Server stopped.");
            }
        }

    //niech pozostanie do testów
    @Deprecated
    public static void main(String[] args) throws InterruptedException {
        ApiServer server = new ApiServer();
        server.runServer();
    }
}

