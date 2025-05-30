package com.project.dao;

import com.project.models.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public interface IUsersDAO {
    void connect() throws SQLException;

    void close() throws SQLException;

    boolean addUser(User user);

    boolean updateUser(User user);

    boolean deleteUser(int userId);

    User getUserWithId(int userId) throws SQLException;

    User getUserByEmail(String email) throws SQLException;

    List<User> getAllUsers() throws SQLException;
}
