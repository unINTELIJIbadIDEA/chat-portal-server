package com.project.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.Callable;

public class Server_ClientSession implements Callable<Void> {

    private final Socket socket;

    public Server_ClientSession(Socket socket) throws IOException {
        this.socket = socket;
    }

    @Override
    public Void call() {

        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            while (true) {
                String message = in.readLine();

                if (message.equals("exit")) {
                    break;
                }

                out.println("[SERVER]: " + message);
            }

        } catch (IOException e) {
            System.out.println("[SYSTEM]: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("[SYSTEM]: " + e.getMessage());
            }
        }

        return null;
    }
}
