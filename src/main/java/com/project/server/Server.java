package com.project.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private final int portNumber;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public Server(int portNumber) {
        this.portNumber = portNumber;
    }

    public void runServer(){

        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New connection from " + socket.getRemoteSocketAddress());

                executor.submit(new Server_ClientSession(socket));
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }


    public static void main(String[] args) {

        Server server = new Server(2137);
        server.runServer();

    }
}
