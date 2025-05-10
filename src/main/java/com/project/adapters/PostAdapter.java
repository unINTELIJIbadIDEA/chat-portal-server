package com.project.adapters;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.project.models.UsersPost;

import java.io.IOException;

public class PostAdapter extends TypeAdapter<UsersPost> {
    @Override
    public void write(JsonWriter out, UsersPost post) throws IOException {
        out.beginObject();
        out.name("postId").value(post.getPostId());
        out.name("userId").value(post.getUserId());
        out.name("name").value(post.getName());
        out.name("surname").value(post.getSurname());
        out.name("content").value(post.getContent());
        out.name("date").value(post.getDate());
        out.endObject();
    }

    @Override
    public UsersPost read(JsonReader in) throws IOException {
        return new Gson().fromJson(in, UsersPost.class);
    }
}
