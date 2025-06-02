package com.project.dao;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.project.adapters.MessageAdapter;
import com.project.models.message.Message;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public interface IMessegeDAO {
    void connect() throws SQLException;

    void close() throws SQLException;

    boolean addMessage(Message newMessage);

    boolean updateMessage(Message message) throws SQLException;

    boolean deleteMessage(int messageId) throws SQLException;

    String getMessageById(int messageId);

    List<Message> getMessagesByChatId(String chatId);

    List<Message> getAllMessages();
}
