package com.project.server;

import com.project.config.ConfigProperties;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.util.logging.Level;

public class Server {

    private static final Logger logger = Logger.getLogger(Server.class.getName());
    static {
        try {
            FileHandler fileHandler = new FileHandler("logs/server.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.INFO);
        } catch (IOException e) {
            System.err.println(" Logger initialization failed: " + e.getMessage());
        }
    }

    private final ExecutorService executor;
    private ServerSocket serverSocket;
    private volatile boolean running = true;


    public Server() {
        executor = Executors.newCachedThreadPool();
    }

    public void runServer() {
        try {
            serverSocket = new ServerSocket(ConfigProperties.getLOCAL_SERVER_PORT());
            logger.info("[SERVER]: Server started, waiting for clients...");
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    logger.info("[SERVER]: New connection from " + socket.getRemoteSocketAddress());
                    executor.submit(new ServerClientHandler(socket));
                } catch (IOException e) {
                    if (running) {
                        logger.warning("[SERVER]: Error while accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            logger.warning("[SERVER]: Failed to start the server: " + e.getMessage());
        } finally {
            stopServer();
        }
    }

    public void stopServer() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                logger.info("[SERVER]: Server socket closed.");
            } catch (IOException e) {
                logger.warning("[SERVER]: Error while closing server socket: " + e.getMessage());
            }
        }
        executor.shutdownNow();
        logger.info("[SERVER]: Executor service shutdown.");
    }

    //niech pozostanie do testów
    @Deprecated
    public static void main(String[] args) {
        Server server = new Server();
        server.runServer();
    }
}
