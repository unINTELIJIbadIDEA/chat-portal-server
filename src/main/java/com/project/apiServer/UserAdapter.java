package com.project.apiServer;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.project.models.User;

import java.io.IOException;

public class UserAdapter extends TypeAdapter<User> {
    @Override
    public void write(JsonWriter out, User user) throws IOException {
        out.beginObject();
        out.name("id").value(user.getId());
        out.name("name").value(user.getName());
        out.name("surname").value(user.getSurname());
        out.name("nickname").value(user.getNickname());
        out.name("email").value(user.getEmail());
        out.name("birthday").value(user.getBirthday());
        out.name("password").value(user.getPassword());
        out.endObject();
    }

    @Override
    public User read(JsonReader in) throws IOException {
        return new Gson().fromJson(in, User.class);
    }
}
