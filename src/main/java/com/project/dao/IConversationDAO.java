package com.project.dao;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public interface IConversationDAO {

    void connect() throws SQLException;

    void close() throws SQLException;

    boolean createConversation(String roomId, String password) throws SQLException

    boolean addUserToConversation(int userId, String roomId) throws SQLException;

    boolean removeUserFromConversation(int userId, String roomId) throws SQLException;

    String getPasswordForRoom(String roomId) throws SQLException;

    List<String> getUserConversations(int userId) throws SQLException;

    boolean conversationExists(String roomId) throws SQLException;
}
