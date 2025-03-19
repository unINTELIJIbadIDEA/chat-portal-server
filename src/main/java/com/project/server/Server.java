package com.project.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private final ExecutorService executor;

    public Server() {
        executor = Executors.newCachedThreadPool();
    }

    public void runServer(){

        try (ServerSocket serverSocket = new ServerSocket(ServerProperties.PORT)) {

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New connection from " + socket.getRemoteSocketAddress());

                executor.submit(new ServerClientHandler(socket));
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }


    public static void main(String[] args) {

        Server server = new Server();
        server.runServer();

    }
}
