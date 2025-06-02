package com.project.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ConversationDAO implements IConversationDAO {
    private final String dbURL;
    private final String dbName;
    private final String dbPassword;
    private Connection connection;

    public ConversationDAO(String dbURL, String dbName, String dbPassword) {
        this.dbURL = dbURL;
        this.dbName = dbName;
        this.dbPassword = dbPassword;
    }

    @Override
    public void connect() throws SQLException {
        connection = DriverManager.getConnection(dbURL, dbName, dbPassword);
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Override
    public boolean createConversation(String roomId, String password) throws SQLException {
        String query = "INSERT INTO conversations (roomId, password) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, roomId);
            stmt.setString(2, password);
            return stmt.executeUpdate() > 0;
        }
    }

    @Override
    public boolean addUserToConversation(int userId, String roomId) throws SQLException {
        String query = "INSERT INTO usersconversations (userId, conversationId) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, userId);
            stmt.setString(2, roomId);
            return stmt.executeUpdate() > 0;
        }
    }

    @Override
    public boolean removeUserFromConversation(int userId, String roomId) throws SQLException {
        String query = "DELETE FROM usersconversations WHERE userId = ? AND conversationId = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, userId);
            stmt.setString(2, roomId);
            return stmt.executeUpdate() > 0;
        }
    }

    @Override
    public String getPasswordForRoom(String roomId) throws SQLException {
        String query = "SELECT password FROM conversations WHERE roomId = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, roomId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("password") : null;
        }
    }

    @Override
    public List<String> getUserConversations(int userId) throws SQLException {
        List<String> conversations = new ArrayList<>();
        String query = "SELECT conversationId FROM usersConversations WHERE userId = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                conversations.add(rs.getString("conversationId"));
            }
        }
        return conversations;
    }

    @Override
    public boolean conversationExists(String roomId) throws SQLException {
        String query = "SELECT 1 FROM conversations WHERE roomId = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, roomId);
            return stmt.executeQuery().next();
        }
    }
}