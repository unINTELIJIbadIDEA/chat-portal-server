package com.project.services;

import com.project.dao.UsersDAO;
import com.project.models.User;
import com.project.security.TokenManager;
import com.project.utils.Config;

import java.sql.SQLException;

public class AuthorizationService {

    private final UsersDAO userDAO;
    private final TokenManager tokenManager;

    public AuthorizationService() {
        this.userDAO = new UsersDAO(Config.getDbUrl(), Config.getDbUsername(), Config.getDbPassword());
        this.tokenManager = new TokenManager(Config.getSecretKey(), Config.getExpirationTime());
    }

    public String authenticateUser(String email, String password) throws SQLException {

        userDAO.connect();
        User user = userDAO.getUserByEmail(email);
        if (user != null && user.getPassword().equals(password)) {
            return tokenManager.generateToken(String.valueOf(user.getId()));
        }
        return null;
    }
}
