package com.project.models.battleship;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class BattleshipGame implements Serializable {
    private final String gameId;
    private final Map<Integer, GameBoard> playerBoards;
    private final Map<Integer, Boolean> playersReady;
    private GameState state;
    private int currentPlayer;
    private int winner;

    public BattleshipGame(String gameId) {
        this.gameId = gameId;
        this.playerBoards = new HashMap<>();
        this.playersReady = new HashMap<>();
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.currentPlayer = -1;
        this.winner = -1;
    }

    public boolean addPlayer(int playerId) {
        if (playerBoards.size() >= 2) {
            return false;
        }

        playerBoards.put(playerId, new GameBoard());
        playersReady.put(playerId, false);

        if (playerBoards.size() == 2) {
            state = GameState.SHIP_PLACEMENT;
        }

        return true;
    }

    public boolean placeShip(int playerId, ShipType shipType, int x, int y, boolean horizontal) {
        if (state != GameState.SHIP_PLACEMENT) {
            return false;
        }

        GameBoard board = playerBoards.get(playerId);
        if (board == null) {
            return false;
        }

        Ship ship = new Ship(shipType);
        boolean placed = board.placeShip(ship, x, y, horizontal);

        if (placed && board.allShipsPlaced()) {
            playersReady.put(playerId, true);
            checkIfReadyToPlay();
        }

        return placed;
    }

    private void checkIfReadyToPlay() {
        if (playersReady.values().stream().allMatch(ready -> ready)) {
            state = GameState.PLAYING;
            // Pierwszy gracz zaczyna
            currentPlayer = playerBoards.keySet().iterator().next();
        }
    }

    public ShotResult takeShot(int playerId, int targetX, int targetY) {
        if (state != GameState.PLAYING || currentPlayer != playerId) {
            return ShotResult.INVALID;
        }

        // Znajdź przeciwnika
        int opponentId = playerBoards.keySet().stream()
                .filter(id -> id != playerId)
                .findFirst()
                .orElse(-1);

        if (opponentId == -1) {
            return ShotResult.INVALID;
        }

        GameBoard opponentBoard = playerBoards.get(opponentId);
        ShotResult result = opponentBoard.receiveShot(targetX, targetY);

        if (result == ShotResult.GAME_OVER) {
            state = GameState.FINISHED;
            winner = playerId;
        } else if (result == ShotResult.MISS) {
            // Zmień turę tylko przy pudle
            currentPlayer = opponentId;
        }

        return result;
    }

    // Getters
    public String getGameId() { return gameId; }
    public GameState getState() { return state; }
    public int getCurrentPlayer() { return currentPlayer; }
    public int getWinner() { return winner; }
    public Map<Integer, GameBoard> getPlayerBoards() { return playerBoards; }
    public Map<Integer, Boolean> getPlayersReady() { return playersReady; }
}
