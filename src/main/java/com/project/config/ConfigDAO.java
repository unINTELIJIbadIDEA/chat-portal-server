package com.project.config;

import com.project.dao.*;

public class ConfigDAO {
    private final IConversationDAO conversationDAO;
    private final IMessegeDAO messageDAO;
    private final IPostsDAO postsDAO;
    private final IUsersDAO usersDAO;

    private static ConfigDAO instance;

    private ConfigDAO(){
        this.conversationDAO = new ConversationDAO(ConfigProperties.getDbUrl(), ConfigProperties.getDbUsername(), ConfigProperties.getDbPassword());
        this.messageDAO = new MessageDAO(ConfigProperties.getDbUrl(), ConfigProperties.getDbUsername(), ConfigProperties.getDbPassword());
        this.postsDAO = new PostsDAO(ConfigProperties.getDbUrl(), ConfigProperties.getDbUsername(), ConfigProperties.getDbPassword());
        this.usersDAO = new UsersDAO(ConfigProperties.getDbUrl(), ConfigProperties.getDbUsername(), ConfigProperties.getDbPassword());
    }

    public static ConfigDAO getInstance(){
        if(instance == null) {
            instance = new ConfigDAO();
        }
        return instance;
    }

    public IConversationDAO getConversationDAO() {
        return conversationDAO;
    }

    public IMessegeDAO getMessageDAO() {
        return messageDAO;
    }

    public IPostsDAO getPostsDAO() {
        return postsDAO;
    }

    public IUsersDAO getUsersDAO() {
        return usersDAO;
    }
}
