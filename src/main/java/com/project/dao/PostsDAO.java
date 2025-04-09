package com.project.dao;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.project.adapters.PostAdapter;
import com.project.models.Post;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PostsDAO {

    private final String dbURL;
    private final String dbName;
    private final String dbPassword;
    private Connection connection;

    public PostsDAO(String dbURL, String dbName, String dbPassword) {
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

    public String getAllPosts() {
        String query = "SELECT posts.postId, posts.userId, user.name, user.surname, posts.content, posts.date FROM posts INNER JOIN user ON user.userId = posts.userId;";
        List<Post> postList = new ArrayList<>();
        try {
            Statement statement = connection.createStatement();
            ResultSet posts = statement.executeQuery(query);
            while (posts.next()) {
                postList.add(new Post(
                        posts.getInt("postId"),
                        posts.getInt("userId"),
                        posts.getString("name"),
                        posts.getString("surname"),
                        posts.getString("content"),
                        posts.getString("date")
                ));
            }

            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(Post.class, new PostAdapter())
                    .create();
            return gson.toJson(postList);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return "";
        }
    }

    public String getAllPostsExcludingId(int excludeId) {
        String query = "SELECT posts.postId, posts.userId, user.name, user.surname, posts.content, posts.date FROM posts INNER JOIN user ON user.userId = posts.userId WHERE posts.userId != ?;";
        List<Post> postList = new ArrayList<>();
        try {
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, excludeId);
            ResultSet posts = statement.executeQuery();
            while (posts.next()) {
                postList.add(new Post(
                        posts.getInt("postId"),
                        posts.getInt("userId"),
                        posts.getString("name"),
                        posts.getString("surname"),
                        posts.getString("content"),
                        posts.getString("date")
                ));
            }

            Gson gson = new Gson();
            return gson.toJson(postList);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return "";
        }
    }

    public boolean deletePostWithId(int deletionId) {
        String query = "DELETE FROM posts WHERE posts.postId = ?";
        try {
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, deletionId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean addPost(Post newPost) {
        String query = "INSERT INTO posts (userId, content, date) VALUES(?, ?, ?)";
        try {
            PreparedStatement statement = connection.prepareStatement(query);
            //statement.setInt(1, newPost.getPostId());
            statement.setInt(1, newPost.getUserId());
            statement.setString(2, newPost.getContent());
            statement.setString(3, newPost.getDate());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public boolean updatePost(Post updatedPost) {
        String query = "UPDATE posts SET content = ? WHERE postId = ?";
        try {
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, updatedPost.getContent());
            statement.setInt(2, updatedPost.getPostId());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }
}