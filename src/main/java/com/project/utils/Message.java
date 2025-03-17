package com.project.utils;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

public record Message(int message_id, int chat_id, int sender_id, String content, LocalDate time) implements Serializable {}
