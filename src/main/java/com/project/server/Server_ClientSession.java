package com.project.server;

import com.project.utils.Message;

import java.io.*;
import java.net.Socket;
import java.time.LocalDate;
import java.util.Date;
import java.util.concurrent.Callable;

public class Server_ClientSession implements Callable<Void> {

    private final Socket socket;

    public Server_ClientSession(Socket socket) throws IOException {
        this.socket = socket;
    }

    @Override
    public Void call() {

        try {
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

            while (true) {
                Message message = (Message) in.readObject();

                if (message.content().equals("exit")) {
                    break;
                }

                System.out.println("Message received from: " + message.sender_id());

                out.writeObject(new Message(0,0, 0, message.content(), LocalDate.now()));
                out.flush();
            }

        } catch (IOException | ClassNotFoundException e) {
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
