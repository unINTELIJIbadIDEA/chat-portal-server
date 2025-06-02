package com.project.services;

import java.sql.SQLException;
import java.util.List;

public interface IConversationService {
    boolean createConversation(int userId, String roomId, String password) throws SQLException;

    boolean joinConversation(int userId, String roomId, String password) throws SQLException;

    boolean leaveConversation(int userId, String roomId) throws SQLException;

    List<String> getUserConversations(int userId) throws SQLException;
}
