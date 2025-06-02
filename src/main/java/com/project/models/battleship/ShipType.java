package com.project.models.battleship;

public enum ShipType {
    CARRIER(5, "Lotniskowiec"),
    BATTLESHIP(4, "Pancernik"),
    CRUISER(3, "Krążownik"),
    SUBMARINE(3, "Łódź podwodna"),
    DESTROYER(2, "Niszczyciel");

    private final int length;
    private final String name;

    ShipType(int length, String name) {
        this.length = length;
        this.name = name;
    }

    public int getLength() {
        return length;
    }

    public String getName() {
        return name;
    }
}
