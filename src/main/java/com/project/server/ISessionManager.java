package com.project.server;

import java.util.concurrent.CopyOnWriteArrayList;

public interface ISessionManager {

    CopyOnWriteArrayList<Server_ClientHandler> getSessions(String roomID);

    String createSession();

    String createSession(String roomID);

    void removeSession(String roomID);

    boolean addClientToSession(String roomID, Server_ClientHandler client);

    boolean removeClientFromSession(String roomID, Server_ClientHandler client);

    boolean sessionExists(String roomID);

    int getSessionSize(String roomID);

}
