package com.project.dao;

import com.google.gson.Gson;
import com.project.models.User;

import java.sql.*;

public class UsersDAO {

    private final String dbURL;
    private final String dbName;
    private final String dbPassword;
    private Connection connection;

    public UsersDAO(String dbURL, String dbName, String dbPassword) {
        this.dbURL = dbURL;
        this.dbName = dbName;
        this.dbPassword = dbPassword;
    }

    public void connect() throws SQLException {
        connection = DriverManager.getConnection(dbURL, dbName, dbPassword);
    }

    public void close() throws SQLException {
        connection.close();
    }

    public boolean addUser(User newUser) {
        String query = "INSERT INTO user (name, surname, nickname, email, birthday, password) VALUES (?, ?, ?, ?, ?, ?)";
        try {
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, newUser.getName());
            statement.setString(2, newUser.getSurname());
            statement.setString(3, newUser.getNickname());
            statement.setString(4, newUser.getEmail());
            statement.setString(5, newUser.getBirthday());
            statement.setString(6, newUser.getPassword());
            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public String getUserWithId(int userId) {
        String query = "SELECT * FROM user WHERE userId = ?";
        try {
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, userId);

            ResultSet resultSet = statement.executeQuery();
            User user = null;
            while (resultSet.next()) {
                user = new User(
                        resultSet.getInt("userId"),
                        resultSet.getString("name"),
                        resultSet.getString("surname"),
                        resultSet.getString("nickname"),
                        resultSet.getString("email"),
                        resultSet.getString("birthday"),
                        resultSet.getString("password")
                );
            }

            Gson gson = new Gson();
            return gson.toJson(user);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "";
        }

    }
}