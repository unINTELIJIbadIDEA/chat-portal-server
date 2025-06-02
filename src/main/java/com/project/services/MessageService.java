package com.project.services;

import com.project.config.ConfigDAO;
import com.project.dao.IMessegeDAO;
import com.project.models.message.Message;

import java.sql.SQLException;
import java.util.List;

public class MessageService implements IMessageService {

    private final IMessegeDAO messageDAO;

    public MessageService() {
        this.messageDAO = ConfigDAO.getInstance().getMessageDAO();
    }

    @Override
    public boolean addMessage(Message newMessage) throws SQLException {


        if (newMessage.content() == null || newMessage.content().trim().isEmpty()) {
            return false;
        }
        try {
            messageDAO.connect();
            return messageDAO.addMessage(newMessage);
        } finally {
            messageDAO.close();
        }
    }

    @Override
    public boolean updateMessage(Message updatedMessage) throws SQLException {
        try {
            messageDAO.connect();
            return messageDAO.updateMessage(updatedMessage);
        } finally {
            messageDAO.close();
        }

    }

    @Override
    public boolean deleteMessage(int messageId) throws SQLException {
        try{
            messageDAO.connect();
            return messageDAO.deleteMessage(messageId);
        } finally {
            messageDAO.close();
        }
    }

    @Override
    public String getMessageById(int messageId) throws SQLException {
        try {
            messageDAO.connect();
            return messageDAO.getMessageById(messageId);
        } finally {
            messageDAO.close();
        }
    }

    @Override
    public List<Message> getMessagesByChatId(String chatId) throws SQLException {
        try{
            messageDAO.connect();
            return messageDAO.getMessagesByChatId(chatId);
        } finally {
            messageDAO.close();
        }
    }

    @Override
    public List<Message> getAllMessages() throws SQLException {
        try {
            messageDAO.connect();
            return messageDAO.getAllMessages();
        } finally {
            messageDAO.close();
        }
    }

}
