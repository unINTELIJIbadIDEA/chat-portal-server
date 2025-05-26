package com.project.models.battleship.messages;

import com.project.models.battleship.ShotResult;

public class ShotResultMessage extends BattleshipMessage {
    private final ShotResult result;
    private final int x;
    private final int y;
    private final int shooterId;

    public ShotResultMessage(int shooterId, String gameId, ShotResult result, int x, int y) {
        super(BattleshipMessageType.SHOT_RESULT, shooterId, gameId);
        this.result = result;
        this.x = x;
        this.y = y;
        this.shooterId = shooterId;
    }

    public ShotResult getResult() { return result; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getShooterId() { return shooterId; }
}

