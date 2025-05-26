package com.project.models.battleship.messages;

import com.project.models.battleship.ShipType;

public class PlaceShipMessage extends BattleshipMessage {
    private static final long serialVersionUID = 1L;
    private final ShipType shipType;
    private final int x;
    private final int y;
    private final boolean horizontal;

    public PlaceShipMessage(int playerId, String gameId, ShipType shipType, int x, int y, boolean horizontal) {
        super(BattleshipMessageType.PLACE_SHIP, playerId, gameId);
        this.shipType = shipType;
        this.x = x;
        this.y = y;
        this.horizontal = horizontal;
    }

    public ShipType getShipType() { return shipType; }
    public int getX() { return x; }
    public int getY() { return y; }
    public boolean isHorizontal() { return horizontal; }
}
