package com.project.models.battleship.messages;

public class PlayerReadyMessage extends BattleshipMessage {
    private static final long serialVersionUID = 1L;

    public PlayerReadyMessage(int playerId, String gameId) {
        super(BattleshipMessageType.PLAYER_READY, playerId, gameId);
    }
}
