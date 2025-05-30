package com.project.services;

import com.project.models.User;

import java.sql.SQLException;
import java.util.List;

public interface IUsersService {
    boolean addUser(User user) throws SQLException;

    boolean updateUser(User user) throws SQLException;

    boolean deleteUser(int id) throws SQLException;

    User getUserById(int id) throws SQLException;

    List<User> getAllUsers() throws SQLException;
}
