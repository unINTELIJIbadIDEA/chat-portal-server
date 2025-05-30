package com.project.services;

import com.project.config.ConfigDAO;
import com.project.dao.IConversationDAO;

import java.sql.SQLException;
import java.util.List;

public class ConversationService implements IConversationService {
    private final IConversationDAO dao;

    public ConversationService(){
        dao = ConfigDAO.getInstance().getConversationDAO();
    }

    @Override
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

    @Override
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

    @Override
    public boolean leaveConversation(int userId, String roomId) throws SQLException {
        try {
            dao.connect();
            return dao.removeUserFromConversation(userId, roomId);
        } finally {
            dao.close();
        }
    }

    @Override
    public List<String> getUserConversations(int userId) throws SQLException {
        try {
            dao.connect();
            return dao.getUserConversations(userId);
        } finally {
            dao.close();
        }
    }
}