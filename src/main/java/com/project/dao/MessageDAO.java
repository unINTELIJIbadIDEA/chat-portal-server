package com.project.dao;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.project.adapters.MessageAdapter;
import com.project.models.Message;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {

    private final String dbURL;
    private final String dbName;
    private final String dbPassword;
    private Connection connection;

    public MessageDAO(String dbURL, String dbName, String dbPassword) {
        this.dbURL = dbURL;
        this.dbName = dbName;
        this.dbPassword = dbPassword;
    }

    public void connect() throws SQLException {
        connection = DriverManager.getConnection(dbURL, dbName, dbPassword);
    }

    public void close() throws SQLException {
        connection.close();
    }

    public boolean addMessage(Message newMessage) {
        String query = "INSERT INTO messages (chat_id, sender_id, content, time) VALUES (?, ?, ?, ?)";
        try {
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, newMessage.chat_id());
            statement.setInt(2, newMessage.sender_id());
            statement.setString(3, newMessage.content());
            statement.setTimestamp(4, Timestamp.valueOf(newMessage.time()));
            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean updateMessage(Message message) throws SQLException {
        String sql = "UPDATE messages SET chat_id = ?, sender_id = ?, content = ?, time = ? WHERE message_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, message.chat_id());
            stmt.setInt(2, message.sender_id());
            stmt.setString(3, message.content());
            stmt.setTimestamp(4, Timestamp.valueOf(message.time()));
            stmt.setInt(5, message.message_id());
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean deleteMessage(int messageId) throws SQLException {
        String sql = "DELETE FROM messages WHERE message_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, messageId);
            return stmt.executeUpdate() > 0;
        }
    }

    public String getMessageById(int messageId) {
        String query = "SELECT * FROM messages WHERE message_id = ?";
        try {
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, messageId);

            ResultSet resultSet = statement.executeQuery();
            Message message = null;
            while (resultSet.next()) {
                message = new Message(
                        resultSet.getInt("message_id"),
                        resultSet.getString("chat_id"),
                        resultSet.getInt("sender_id"),
                        resultSet.getString("content"),
                        resultSet.getTimestamp("time").toLocalDateTime()
                );
            }

            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(Message.class, new MessageAdapter())
                    .create();;
            return gson.toJson(message);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return "";
        }
    }

    public List<Message> getMessagesByChatId(String chatId) {
        String query = "SELECT * FROM message WHERE chat_id = ?";
        List<Message> messages = new ArrayList<>();
        try {
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, chatId);

            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                Message message = new Message(
                        resultSet.getInt("message_id"),
                        resultSet.getString("chat_id"),
                        resultSet.getInt("sender_id"),
                        resultSet.getString("content"),
                        resultSet.getTimestamp("time").toLocalDateTime()
                );
                messages.add(message);
            }

            return messages;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return messages;
        }
    }

    public List<Message> getAllMessages() {
        String query = "SELECT * FROM messages";
        List<Message> messages = new ArrayList<>();
        try {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(query);
            while (resultSet.next()) {
                Message message = new Message(
                        resultSet.getInt("message_id"),
                        resultSet.getString("chat_id"),
                        resultSet.getInt("sender_id"),
                        resultSet.getString("content"),
                        resultSet.getTimestamp("time").toLocalDateTime()
                );
                messages.add(message);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return messages;
    }

}
