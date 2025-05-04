package com.project.server;

import com.project.utils.Config;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private final ExecutorService executor;
    private ServerSocket serverSocket;
    private volatile boolean running = true;

    public Server() {
        executor = Executors.newCachedThreadPool();
    }

    public void runServer() {
        try {
            serverSocket = new ServerSocket(Config.getLOCAL_SERVER_PORT());
            System.out.println("[SERVER]: Server started, waiting for clients...");
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    System.out.println("[SERVER]: New connection from " + socket.getRemoteSocketAddress());
                    executor.submit(new ServerClientHandler(socket));
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[SERVER]: Error accepting connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[SERVER]: Failed to start: " + e.getMessage());
        } finally {
            stopServer();
        }
    }

    public void stopServer() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                System.out.println("[SERVER]: Socket closed.");
            } catch (IOException e) {
                System.err.println("[SERVER]: Error closing socket: " + e.getMessage());
            }
        }
        executor.shutdownNow();
        System.out.println("[SERVER]: Executor shutdown.");
    }

    //niech pozostanie do testów
    @Deprecated
    public static void main(String[] args) {
        Server server = new Server();
        server.runServer();
    }
}
