package com.project.config;

import com.project.services.*;

public class ConfigService {
    private final IAuthorizationService authorizationService;
    private final IConversationService conversationService;
    private final IMessageService messageService;
    private final IUsersService usersService;

    private static ConfigService instance;

    private ConfigService() {
        this.authorizationService = new AuthorizationService();
        this.conversationService = new ConversationService();
        this.messageService = new MessageService();
        this.usersService = new UsersService();
    }

    public static ConfigService getInstance() {
        if (instance == null) {
            instance = new ConfigService();
        }
        return instance;
    }

    public IAuthorizationService getAuthorizationService() {
        return authorizationService;
    }

    public IConversationService getConversationService() {
        return conversationService;
    }

    public IMessageService getMessageService() {
        return messageService;
    }

    public IUsersService getUsersService() {
        return usersService;
    }
}
