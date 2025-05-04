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
        try {
            dao.connect();
            return dao.addUser(user);
        } finally {
            dao.close();
        }
    }

    public boolean updateUser(User user) throws SQLException {
        try {
            dao.connect();
            return dao.updateUser(user);
        } finally {
            dao.close();
        }
    }

    public boolean deleteUser(int id) throws SQLException {
        try {
            dao.connect();
            return dao.deleteUser(id);
        } finally {
            dao.close();
        }
    }

    public User getUserById(int id) throws SQLException {
        try {
            dao.connect();
            return dao.getUserWithId(id);
        } finally {
            dao.close();
        }
    }

    public List<User> getAllUsers() throws SQLException {
        try {
            dao.connect();
            return dao.getAllUsers();
        } finally {
            dao.close();
        }
    }
}