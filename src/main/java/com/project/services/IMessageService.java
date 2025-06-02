package com.project.services;

import com.project.models.message.Message;

import java.sql.SQLException;
import java.util.List;

public interface IMessageService {
    boolean addMessage(Message newMessage) throws SQLException;

    boolean updateMessage(Message updatedMessage) throws SQLException;

    boolean deleteMessage(int messageId) throws SQLException;

    String getMessageById(int messageId) throws SQLException;

    List<Message> getMessagesByChatId(String chatId) throws SQLException;

    List<Message> getAllMessages() throws SQLException;
}
