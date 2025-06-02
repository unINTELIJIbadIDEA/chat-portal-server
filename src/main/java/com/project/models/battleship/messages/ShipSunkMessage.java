package com.project.models.battleship.messages;

import com.project.models.battleship.Position;
import java.util.List;

public class ShipSunkMessage extends BattleshipMessage {
    private static final long serialVersionUID = 1L;
    private final List<Position> shipPositions;
    private final int shooterId;

    public ShipSunkMessage(int shooterId, String gameId, List<Position> shipPositions) {
        super(BattleshipMessageType.SHIP_SUNK, shooterId, gameId);
        this.shipPositions = shipPositions;
        this.shooterId = shooterId;
    }

    public List<Position> getShipPositions() { return shipPositions; }
    public int getShooterId() { return shooterId; }
}
