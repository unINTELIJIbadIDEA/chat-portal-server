package com.project.apiServer;

public class Post {
    //userId is an id of owner of the post
    private int postId, userId;
    private String name, surname, content, date;

    public Post() {};

    public Post(int postId, int userId, String name, String surname, String content, String date) {
        this.postId = postId;
        this.userId = userId;
        this.name = name;
        this.surname = surname;
        this.content = content;
        this.date = date;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getPostId() {
        return postId;
    }

    public void setPostId(int postId) {
        this.postId = postId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }
}