package com.project.models.battleship;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class GameBoard implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int BOARD_SIZE = 10;
    private final Cell[][] board;
    private final List<PlacedShip> ships;
    private final List<ShipType> placedShipTypes;

    public GameBoard() {
        board = new Cell[BOARD_SIZE][BOARD_SIZE];
        ships = new ArrayList<>();
        placedShipTypes = new ArrayList<>();
        initializeBoard();
    }

    private void initializeBoard() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                board[i][j] = new Cell();
            }
        }
    }

    public boolean placeShip(Ship ship, int startX, int startY, boolean horizontal) {
        // Sprawdź czy ten typ statku już został umieszczony
        if (placedShipTypes.contains(ship.getType())) {
            return false;
        }

        if (!canPlaceShip(ship, startX, startY, horizontal)) {
            return false;
        }

        List<Position> positions = new ArrayList<>();
        for (int i = 0; i < ship.getLength(); i++) {
            int x = horizontal ? startX + i : startX;
            int y = horizontal ? startY : startY + i;
            board[x][y].setShip(ship);
            positions.add(new Position(x, y));
        }

        ships.add(new PlacedShip(ship, positions, horizontal));
        placedShipTypes.add(ship.getType());
        return true;
    }

    private boolean canPlaceShip(Ship ship, int startX, int startY, boolean horizontal) {
        int endX = horizontal ? startX + ship.getLength() - 1 : startX;
        int endY = horizontal ? startY : startY + ship.getLength() - 1;

        // Czy statek mieści się w planszy?
        if (endX >= BOARD_SIZE || endY >= BOARD_SIZE || startX < 0 || startY < 0) {
            return false;
        }

        // Sprawdź komórki statku i ich otoczenie
        for (int i = 0; i < ship.getLength(); i++) {
            int x = horizontal ? startX + i : startX;
            int y = horizontal ? startY : startY + i;

            // Sprawdź otoczenie każdej komórki statku
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int checkX = x + dx;
                    int checkY = y + dy;

                    if (checkX >= 0 && checkX < BOARD_SIZE && checkY >= 0 && checkY < BOARD_SIZE) {
                        if (board[checkX][checkY].hasShip()) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    public ShotResult receiveShot(int x, int y) {
        if (x < 0 || x >= BOARD_SIZE || y < 0 || y >= BOARD_SIZE) {
            return ShotResult.INVALID;
        }

        Cell cell = board[x][y];
        if (cell.isShot()) {
            return ShotResult.ALREADY_SHOT;
        }

        cell.setShot(true);

        if (cell.hasShip()) {
            Ship ship = cell.getShip();
            ship.hit();

            if (ship.isSunk()) {
                markSurroundingCells(ship);
                if (allShipsSunk()) {
                    return ShotResult.GAME_OVER;
                }
                return ShotResult.SUNK;
            }
            return ShotResult.HIT;
        }

        return ShotResult.MISS;
    }

    private void markSurroundingCells(Ship sunkShip) {
        for (PlacedShip placedShip : ships) {
            if (placedShip.getShip() == sunkShip) {
                for (Position pos : placedShip.getPositions()) {
                    markSurrounding(pos.getX(), pos.getY());
                }
                break;
            }
        }
    }

    private void markSurrounding(int centerX, int centerY) {
        for (int x = Math.max(0, centerX - 1); x <= Math.min(BOARD_SIZE - 1, centerX + 1); x++) {
            for (int y = Math.max(0, centerY - 1); y <= Math.min(BOARD_SIZE - 1, centerY + 1); y++) {
                if (!board[x][y].hasShip()) {
                    board[x][y].setShot(true);
                }
            }
        }
    }

    private boolean allShipsSunk() {
        return ships.stream().allMatch(placedShip -> placedShip.getShip().isSunk());
    }

    public boolean allShipsPlaced() {
        return placedShipTypes.size() == ShipType.values().length;
    }

    public Cell[][] getBoard() {
        return board;
    }

    public List<PlacedShip> getShips() {
        return ships;
    }

    public static int getBoardSize() {
        return BOARD_SIZE;
    }
}
