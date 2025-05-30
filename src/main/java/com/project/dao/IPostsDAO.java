package com.project.dao;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.project.adapters.PostAdapter;
import com.project.models.Post;
import com.project.models.UsersPost;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public interface IPostsDAO {
    void connect() throws SQLException;

    void close() throws SQLException;

    String getAllPosts();

    String getAllPostsExcludingId(int excludeId);

    String getAllPostsWithUserId(int userId);

    boolean deletePostWithId(int deletionId);

    boolean addPost(Post newPost);

    boolean updatePost(int postId, String newContent) throws SQLException;
}
