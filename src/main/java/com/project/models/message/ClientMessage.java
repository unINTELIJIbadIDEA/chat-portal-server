package com.project.models.message;

import java.io.Serializable;

public record ClientMessage (String content, String chatId, String token) implements Serializable { }
