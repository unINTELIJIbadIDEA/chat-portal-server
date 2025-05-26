package com.project.models.battleship;

import java.io.Serializable;
import java.util.List;

public class PlacedShip implements Serializable {
    private final Ship ship;
    private final List<Position> positions;
    private final boolean horizontal;

    public PlacedShip(Ship ship, List<Position> positions, boolean horizontal) {
        this.ship = ship;
        this.positions = positions;
        this.horizontal = horizontal;
    }

    public Ship getShip() {
        return ship;
    }

    public List<Position> getPositions() {
        return positions;
    }

    public boolean isHorizontal() {
        return horizontal;
    }
}
