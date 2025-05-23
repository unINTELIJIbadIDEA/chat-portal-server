package com.project.models;

import com.project.models.message.Message;
import com.project.server.ServerClientHandler;

import java.util.concurrent.CopyOnWriteArrayList;

public class Conversation {
    private final String roomId;
    private final CopyOnWriteArrayList<ServerClientHandler> observers = new CopyOnWriteArrayList<>();

    public Conversation(String roomId) {
        this.roomId = roomId;
    }

    public void registerObserver(ServerClientHandler observer) {
        observers.add(observer);
        //System.out.println("Registered Observer: " + observer + " for " + roomId);
    }

    public void removeObserver(ServerClientHandler observer) {
        observers.remove(observer);
    }

    public void notifyObservers(Message message) {

        observers.forEach(observer -> observer.update(message));
    }

    public CopyOnWriteArrayList<ServerClientHandler> getObservers() {
        return observers;
    }

    public void broadcast(Message message) {
        notifyObservers(message);
    }

    public int getObserverCount() {
        return observers.size();
    }
}
