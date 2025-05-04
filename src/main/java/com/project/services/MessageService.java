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
        try {
            messageDAO.connect();
            boolean result = messageDAO.addMessage(newMessage);
            return result;
        } finally {
            messageDAO.close();
        }
    }

    public boolean updateMessage(Message updatedMessage) throws SQLException {
        try {
            messageDAO.connect();
            return messageDAO.updateMessage(updatedMessage);
        } finally {
            messageDAO.close();
        }

    }

    public boolean deleteMessage(int messageId) throws SQLException {
        try{
            messageDAO.connect();
            return messageDAO.deleteMessage(messageId);
        } finally {
            messageDAO.close();
        }
    }

    public String getMessageById(int messageId) throws SQLException {
        try {
            messageDAO.connect();
            return messageDAO.getMessageById(messageId);
        } finally {
            messageDAO.close();
        }
    }

    public List<Message> getMessagesByChatId(String chatId) throws SQLException {
        try{
            messageDAO.connect();
            return messageDAO.getMessagesByChatId(chatId);
        } finally {
            messageDAO.close();
        }
    }

    public List<Message> getAllMessages() throws SQLException {
        try {
            messageDAO.connect();
            return messageDAO.getAllMessages();
        } finally {
            messageDAO.close();
        }
    }

}
