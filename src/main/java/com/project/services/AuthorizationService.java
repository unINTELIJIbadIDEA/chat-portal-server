package com.project.services;

import com.project.config.ConfigDAO;
import com.project.dao.IUsersDAO;
import com.project.models.User;
import com.project.security.TokenManager;
import com.project.config.ConfigProperties;

import java.sql.SQLException;

public class AuthorizationService implements IAuthorizationService {

    private final IUsersDAO userDAO;
    private final TokenManager tokenManager;

    public AuthorizationService() {
        this.userDAO = ConfigDAO.getInstance().getUsersDAO();
        this.tokenManager = new TokenManager(ConfigProperties.getSecretKey(), ConfigProperties.getExpirationTime());
    }

    @Override
    public String authenticateUser(String email, String password) throws SQLException {

        try {
            userDAO.connect();
            User user = userDAO.getUserByEmail(email);
            if (user != null && user.getPassword().equals(password)) {
                return tokenManager.generateToken(String.valueOf(user.getId()));
            }
        } finally {
            userDAO.close();
        }
        return null;
    }
}
