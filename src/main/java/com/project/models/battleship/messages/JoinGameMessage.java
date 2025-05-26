package com.project.models.battleship.messages;

public class JoinGameMessage extends BattleshipMessage {
    private final String chatId;

    public JoinGameMessage(int playerId, String gameId, String chatId) {
        super(BattleshipMessageType.JOIN_GAME, playerId, gameId);
        this.chatId = chatId;
    }

    public String getChatId() { return chatId; }
}