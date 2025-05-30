package com.project.services;

import java.sql.SQLException;

public interface IAuthorizationService {
    String authenticateUser(String email, String password) throws SQLException;
}
