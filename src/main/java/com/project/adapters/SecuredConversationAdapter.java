package com.project.adapters;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.project.models.SecuredConversation;

import java.io.IOException;

public class SecuredConversationAdapter extends TypeAdapter<SecuredConversation> {

    @Override
    public void write(JsonWriter out, SecuredConversation conversation) throws IOException {
        out.beginObject();
        out.name("roomId").value(conversation.getRoomId());
        out.name("observerCount").value(conversation.getObserverCount(null)); // Uwaga: to może wymagać refaktoru
        out.endObject();
    }

    @Override
    public SecuredConversation read(JsonReader in) throws IOException {
        // Pomijamy deserializację, ponieważ nie powinno się tworzyć SecuredConversation przez REST
        return null;
    }
}