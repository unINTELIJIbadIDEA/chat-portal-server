package com.project.utils;

import com.project.server.Server_ClientHandler;

import java.util.concurrent.CopyOnWriteArrayList;

public class Room{
    private final String roomId;
    private final CopyOnWriteArrayList<Server_ClientHandler> observers = new CopyOnWriteArrayList<>();

    public Room(String roomId) {
        this.roomId = roomId;
    }

    public void registerObserver(Server_ClientHandler observer) {
        observers.add(observer);
    }

    public void removeObserver(Server_ClientHandler observer) {
        observers.remove(observer);
    }

    public void notifyObservers(Message message) {

        observers.forEach(observer -> observer.update(message));
    }

    public CopyOnWriteArrayList<Server_ClientHandler> getObservers() {
        return observers;
    }

    public void broadcast(Message message) {
        notifyObservers(message);
    }

    public int getObserverCount() {
        return observers.size();
    }
}
