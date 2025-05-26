package com.project.models.battleship;

import java.io.Serializable;

public class Ship implements Serializable {
    private final int length;
    private final ShipType type;
    private int hits;
    private boolean sunk;

    public Ship(ShipType type) {
        this.type = type;
        this.length = type.getLength();
        this.hits = 0;
        this.sunk = false;
    }

    public void hit() {
        hits++;
        if (hits >= length) {
            sunk = true;
        }
    }

    public boolean isSunk() {
        return sunk;
    }

    public ShipType getType() {
        return type;
    }

    public int getLength() {
        return length;
    }

    public int getHits() {
        return hits;
    }
}

