package com.project.models.battleship.messages;


public class TakeShotMessage extends BattleshipMessage {
    private static final long serialVersionUID = 1L;
    private final int x;
    private final int y;

    public TakeShotMessage(int playerId, String gameId, int x, int y) {
        super(BattleshipMessageType.TAKE_SHOT, playerId, gameId);
        this.x = x;
        this.y = y;
    }

    public int getX() { return x; }
    public int getY() { return y; }
}
