package com.project.utils;

import java.io.Serializable;
import java.time.LocalDate;

public record Message(int message_id, String chat_id, int sender_id, String content, LocalDate time) implements Serializable {}
