package com.project.models.battleship.messages;

import java.io.Serializable;

public abstract class BattleshipMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private final BattleshipMessageType type;
    private final int playerId;
    private final String gameId;

    protected BattleshipMessage(BattleshipMessageType type, int playerId, String gameId) {
        this.type = type;
        this.playerId = playerId;
        this.gameId = gameId;
    }

    public BattleshipMessageType getType() { return type; }
    public int getPlayerId() { return playerId; }
    public String getGameId() { return gameId; }
}

