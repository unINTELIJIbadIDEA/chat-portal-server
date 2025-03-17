package com.project.server;

import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class SessionManager implements ISessionManager {

    private static final SessionManager INSTANCE = new SessionManager();

    //ConcurentMap to mapa która lepiej działa na wątkach, a CopyOnWriteList to Lista która lepiej działa na wątkach
    private final ConcurrentMap<String, CopyOnWriteArrayList<Server_ClientHandler>> sessions;

    private SessionManager() {
        sessions = new ConcurrentHashMap<String, CopyOnWriteArrayList<Server_ClientHandler>>();
    }

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    @Override
    public CopyOnWriteArrayList<Server_ClientHandler> getSessions(String roomID) {
        return new CopyOnWriteArrayList<>(sessions.getOrDefault(roomID, new CopyOnWriteArrayList<>()));
    }

    @Override
    public String createSession() {
        return createSession(UUID.randomUUID().toString());
    }

    @Override
    public String createSession(String roomID) {
        sessions.put(roomID, new CopyOnWriteArrayList<>());
        return roomID;
    }

    @Override
    public void removeSession(String roomID) {
        sessions.remove(roomID);
    }

    @Override
    public boolean addClientToSession(String roomID, Server_ClientHandler client) {
        sessions.computeIfAbsent(roomID, k -> new CopyOnWriteArrayList<>()).add(client);
        return true;
    }

    @Override
    public boolean removeClientFromSession(String roomID, Server_ClientHandler client) {
        CopyOnWriteArrayList<Server_ClientHandler> session = sessions.get(roomID);
        if (session != null) {
            boolean removed = session.remove(client);
            if (session.isEmpty()) {
                removeSession(roomID);
            }
            return removed;
        }
        return false;
    }

    @Override
    public boolean sessionExists(String roomID) {
        return sessions.containsKey(roomID);
    }

    @Override
    public int getSessionSize(String roomID) {
        CopyOnWriteArrayList<Server_ClientHandler> session = sessions.get(roomID);
        return (session != null) ? session.size() : 0;
    }

}
