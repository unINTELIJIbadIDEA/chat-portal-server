package com.project.dao;

import com.project.models.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UsersDAO {
    private final String dbURL;
    private final String dbUser;
    private final String dbPassword;
    private Connection connection;

    public UsersDAO(String dbURL, String dbUser, String dbPassword) {
        this.dbURL = dbURL;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public void connect() throws SQLException {
        connection = DriverManager.getConnection(dbURL, dbUser, dbPassword);
    }

    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    public boolean addUser(User user) {
        String sql = "INSERT INTO `user` (name, surname, nickname, email, birthday, password) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, user.getName());
            stmt.setString(2, user.getSurname());
            stmt.setString(3, user.getNickname());
            stmt.setString(4, user.getEmail());
            stmt.setDate(5, Date.valueOf(user.getBirthday())); // zakładamy format YYYY-MM-DD
            stmt.setString(6, user.getPassword());
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        user.setId(keys.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("addUser error: " + e.getMessage());
        }
        return false;
    }

    public boolean updateUser(User user) {
        String sql = "UPDATE `user` SET name = ?, surname = ?, nickname = ?, email = ?, birthday = ?, password = ? WHERE userId = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, user.getName());
            stmt.setString(2, user.getSurname());
            stmt.setString(3, user.getNickname());
            stmt.setString(4, user.getEmail());
            stmt.setDate(5, Date.valueOf(user.getBirthday()));
            stmt.setString(6, user.getPassword());
            stmt.setInt(7, user.getId());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("updateUser error: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteUser(int userId) {
        String sql = "DELETE FROM `user` WHERE userId = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("deleteUser error: " + e.getMessage());
            return false;
        }
    }

    public User getUserWithId(int userId) throws SQLException {
        String sql = "SELECT * FROM `user` WHERE userId = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("userId"),
                            rs.getString("name"),
                            rs.getString("surname"),
                            rs.getString("nickname"),
                            rs.getString("email"),
                            rs.getDate("birthday").toString(),
                            rs.getString("password")
                    );
                }
            }
        }
        return null;
    }

    public User getUserByEmail(String email) throws SQLException {
        String sql = "SELECT * FROM `user` WHERE email = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("userId"),
                            rs.getString("name"),
                            rs.getString("surname"),
                            rs.getString("nickname"),
                            rs.getString("email"),
                            rs.getDate("birthday").toString(),
                            rs.getString("password")
                    );
                }
            }
        }
        return null;
    }

    public List<User> getAllUsers() throws SQLException {
        String sql = "SELECT * FROM `user`";
        List<User> list = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new User(
                        rs.getInt("userId"),
                        rs.getString("name"),
                        rs.getString("surname"),
                        rs.getString("nickname"),
                        rs.getString("email"),
                        rs.getDate("birthday").toString(),
                        rs.getString("password")
                ));
            }
        }
        return list;
    }
}
