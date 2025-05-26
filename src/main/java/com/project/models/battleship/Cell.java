package com.project.models.battleship;

import java.io.Serializable;

public class Cell implements Serializable {
    private Ship ship;
    private boolean shot;

    public Cell() {
        this.ship = null;
        this.shot = false;
    }

    public boolean hasShip() {
        return ship != null;
    }

    public Ship getShip() {
        return ship;
    }

    public void setShip(Ship ship) {
        this.ship = ship;
    }

    public boolean isShot() {
        return shot;
    }

    public void setShot(boolean shot) {
        this.shot = shot;
    }
}