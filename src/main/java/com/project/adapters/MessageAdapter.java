package com.project.adapters;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.project.models.message.Message;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MessageAdapter extends TypeAdapter<Message> {

    @Override
    public void write(JsonWriter out, Message message) throws IOException {
        out.beginObject();
        out.name("message_id").value(message.message_id());
        out.name("chat_id").value(message.chat_id());
        out.name("sender_id").value(message.sender_id());
        out.name("content").value(message.content());
        out.name("time").value(message.time().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        out.endObject();
    }

    @Override
    public Message read(JsonReader in) throws IOException {
        int messageId = 0;
        String chatId = null;
        int senderId = 0;
        String content = null;
        LocalDateTime time = null;

        in.beginObject();
        while (in.hasNext()) {
            String name = in.nextName();
            switch (name) {
                case "message_id":
                    messageId = in.nextInt();
                    break;
                case "chat_id":
                    chatId = in.nextString();
                    break;
                case "sender_id":
                    senderId = in.nextInt();
                    break;
                case "content":
                    content = in.nextString();
                    break;
                case "time":
                    time = LocalDateTime.parse(in.nextString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    break;
                default:
                    in.skipValue();
                    break;
            }
        }
        in.endObject();
        return new Message(messageId, chatId, senderId, content, time);
    }
}
