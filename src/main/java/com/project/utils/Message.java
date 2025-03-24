package com.project.utils;

import java.io.Serializable;
import java.time.LocalDateTime;

public record Message(int message_id, String chat_id, int sender_id, String content, LocalDateTime time) implements Serializable {}
