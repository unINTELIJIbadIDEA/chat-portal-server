package com.project.models.battleship;

import java.io.Serializable;
import java.sql.Timestamp;

public class BattleshipGameInfo implements Serializable {
    private final String gameId;
    private final String gameName;
    private final String chatId;
    private final int player1Id;
    private final Integer player2Id;
    private final String status;
    private final Integer winnerId;
    private final Timestamp createdAt;

    public BattleshipGameInfo(String gameId, String gameName, String chatId, int player1Id,
                              Integer player2Id, String status, Integer winnerId, Timestamp createdAt) {
        this.gameId = gameId;
        this.gameName = gameName;
        this.chatId = chatId;
        this.player1Id = player1Id;
        this.player2Id = player2Id;
        this.status = status;
        this.winnerId = winnerId;
        this.createdAt = createdAt;
    }

    public String getGameId() { return gameId; }
    public String getGameName() { return gameName; }
    public String getChatId() { return chatId; }
    public int getPlayer1Id() { return player1Id; }
    public Integer getPlayer2Id() { return player2Id; }
    public String getStatus() { return status; }
    public Integer getWinnerId() { return winnerId; }
    public Timestamp getCreatedAt() { return createdAt; }
}