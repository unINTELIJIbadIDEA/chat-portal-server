package com.project.services;

import com.project.config.ConfigDAO;
import com.project.dao.IUsersDAO;
import com.project.models.User;

import java.sql.SQLException;
import java.util.List;

public class UsersService implements IUsersService {
    private final IUsersDAO dao;

    public UsersService() {
        this.dao = ConfigDAO.getInstance().getUsersDAO();
    }

    @Override
    public boolean addUser(User user) throws SQLException {
        try {
            dao.connect();
            return dao.addUser(user);
        } finally {
            dao.close();
        }
    }

    @Override
    public boolean updateUser(User user) throws SQLException {
        try {
            dao.connect();
            return dao.updateUser(user);
        } finally {
            dao.close();
        }
    }

    @Override
    public boolean deleteUser(int id) throws SQLException {
        try {
            dao.connect();
            return dao.deleteUser(id);
        } finally {
            dao.close();
        }
    }

    @Override
    public User getUserById(int id) throws SQLException {
        try {
            dao.connect();
            return dao.getUserWithId(id);
        } finally {
            dao.close();
        }
    }

    @Override
    public List<User> getAllUsers() throws SQLException {
        try {
            dao.connect();
            return dao.getAllUsers();
        } finally {
            dao.close();
        }
    }
}