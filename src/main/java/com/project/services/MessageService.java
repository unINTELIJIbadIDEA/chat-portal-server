package com.project.services;

import com.project.dao.MessageDAO;
import com.project.models.message.Message;
import com.project.utils.Config;

import java.sql.SQLException;
import java.util.List;

public class MessageService {

    private final MessageDAO messageDAO;

    public MessageService() {
        this.messageDAO = new MessageDAO(Config.getDbUrl(), Config.getDbUsername(), Config.getDbPassword());
    }

    public boolean addMessage(Message newMessage) throws SQLException {


        if (newMessage.content() == null || newMessage.content().trim().isEmpty()) {
            return false;
        }
        messageDAO.connect();
        boolean result = messageDAO.addMessage(newMessage);
        messageDAO.close();
        return result;
    }

    public boolean updateMessage(Message updatedMessage) throws SQLException {
        messageDAO.connect();
        boolean result = messageDAO.updateMessage(updatedMessage);
        messageDAO.close();
        return result;
    }

    public boolean deleteMessage(int messageId) throws SQLException {
        messageDAO.connect();
        boolean result = messageDAO.deleteMessage(messageId);
        messageDAO.close();
        return result;
    }

    public String getMessageById(int messageId) throws SQLException {
        messageDAO.connect();
        String result = messageDAO.getMessageById(messageId);
        messageDAO.close();
        return result;
    }

    public List<Message> getMessagesByChatId(String chatId) throws SQLException {
        messageDAO.connect();
        List<Message> result = messageDAO.getMessagesByChatId(chatId);
        messageDAO.close();
        return result;
    }

    public List<Message> getAllMessages() throws SQLException {
        messageDAO.connect();
        List<Message> result = messageDAO.getAllMessages();
        messageDAO.close();
        return result;
    }

}
