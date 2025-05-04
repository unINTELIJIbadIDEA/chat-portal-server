package com.project.services;

import com.project.dao.UsersDAO;
import com.project.models.User;
import com.project.utils.Config;

import java.sql.SQLException;
import java.util.List;

public class UsersService {
    private final UsersDAO dao;

    public UsersService() {
        this.dao = new UsersDAO(
                Config.getDbUrl(),
                Config.getDbUsername(),
                Config.getDbPassword()
        );
    }

    public boolean addUser(User user) throws SQLException {
        dao.connect();
        boolean result = dao.addUser(user);
        dao.close();
        return result;
    }

    public boolean updateUser(User user) throws SQLException {
        dao.connect();
        boolean result = dao.updateUser(user);
        dao.close();
        return result;
    }

    public boolean deleteUser(int id) throws SQLException {
        dao.connect();
        boolean result = dao.deleteUser(id);
        dao.close();
        return result;
    }

    public User getUserById(int id) throws SQLException {
        dao.connect();
        User user = dao.getUserWithId(id);
        dao.close();
        return user;
    }

    public List<User> getAllUsers() throws SQLException {
        dao.connect();
        List<User> list = dao.getAllUsers();
        dao.close();
        return list;
    }
}