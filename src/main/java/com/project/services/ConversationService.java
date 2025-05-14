package com.project.services;

import com.project.dao.ConversationDAO;
import com.project.utils.Config;

import java.sql.SQLException;
import java.util.List;

public class ConversationService {
    private final ConversationDAO dao = new ConversationDAO(
            Config.getDbUrl(),
            Config.getDbUsername(),
            Config.getDbPassword()
    );

    public boolean createConversation(int userId, String roomId, String password) throws SQLException {
        try {
            dao.connect();
            if (dao.conversationExists(roomId)) return false;
            boolean created = dao.createConversation(roomId, password);
            return created && dao.addUserToConversation(userId, roomId);
        } finally {
            dao.close();
        }
    }

    public boolean joinConversation(int userId, String roomId, String password) throws SQLException {
        try {
            dao.connect();
            String storedPassword = dao.getPasswordForRoom(roomId);
            if (storedPassword == null || !storedPassword.equals(password)) return false;
            return dao.addUserToConversation(userId, roomId);
        } finally {
            dao.close();
        }
    }

    public boolean leaveConversation(int userId, String roomId) throws SQLException {
        try {
            dao.connect();
            return dao.removeUserFromConversation(userId, roomId);
        } finally {
            dao.close();
        }
    }

    public List<String> getUserConversations(int userId) throws SQLException {
        try {
            dao.connect();
            return dao.getUserConversations(userId);
        } finally {
            dao.close();
        }
    }
}