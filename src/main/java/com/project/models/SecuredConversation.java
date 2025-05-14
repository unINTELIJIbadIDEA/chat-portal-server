package com.project.models;

import com.project.models.message.Message;
import com.project.server.ServerClientHandler;

public class SecuredConversation {
    private final String roomId;
    private final String password;
    private final Conversation conversation;

    public SecuredConversation(String roomId, String password) {
        this.roomId = roomId;
        this.password = password;
        this.conversation = new Conversation(roomId);
    }

    public boolean authenticate(String attempt) {
        return this.password.equals(attempt);
    }

    public void registerObserver(String attempt, ServerClientHandler observer) {
        if (!authenticate(attempt)) {
            throw new SecurityException("Invalid password for room " + roomId);
        }
        conversation.registerObserver(observer);
    }

    public void removeObserver(String attempt, ServerClientHandler observer) {
        if (!authenticate(attempt)) {
            throw new SecurityException("Invalid password for room " + roomId);
        }
        conversation.removeObserver(observer);
    }

    public void broadcast(String attempt, Message message) {
        if (!authenticate(attempt)) {
            throw new SecurityException("Invalid password for room " + roomId);
        }
        conversation.broadcast(message);
    }

    public int getObserverCount(String attempt) {
        if (!authenticate(attempt)) {
            throw new SecurityException("Invalid password for room " + roomId);
        }
        return conversation.getObserverCount();
    }

    public String getRoomId() {
        return roomId;
    }
}
