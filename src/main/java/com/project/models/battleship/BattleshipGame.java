package com.project.models.battleship;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class BattleshipGame implements Serializable {
    private static final long serialVersionUID = 4431750269896121533L;
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
        System.out.println("[BATTLESHIP GAME]: Place ship request - Player: " + playerId +
                ", Ship: " + shipType + ", Position: (" + x + "," + y + "), Horizontal: " + horizontal);

        if (state != GameState.SHIP_PLACEMENT) {
            System.out.println("[BATTLESHIP GAME]: Wrong state for placing ships: " + state);
            return false;
        }

        GameBoard board = playerBoards.get(playerId);
        if (board == null) {
            System.out.println("[BATTLESHIP GAME]: Board not found for player: " + playerId);
            return false;
        }

        Ship ship = new Ship(shipType);
        boolean placed = board.placeShip(ship, x, y, horizontal);

        if (placed) {
            System.out.println("[BATTLESHIP GAME]: Ship placed successfully!");

            // NIE zmieniaj automatycznie status na ready - czekaj na PLAYER_READY message
            if (board.allShipsPlaced()) {
                System.out.println("[BATTLESHIP GAME]: All ships placed for player " + playerId +
                        ", waiting for PLAYER_READY message");
            }
        } else {
            System.out.println("[BATTLESHIP GAME]: Failed to place ship!");
        }

        return placed;
    }

    private void checkIfReadyToPlay() {
        System.out.println("[BATTLESHIP GAME]: Checking if ready to play...");
        System.out.println("[BATTLESHIP GAME]: Players ready: " + playersReady);

        boolean allReady = playersReady.size() == 2 &&
                playersReady.values().stream().allMatch(ready -> ready != null && ready);

        System.out.println("[BATTLESHIP GAME]: All players ready: " + allReady);

        if (allReady) {
            state = GameState.PLAYING;
            // Pierwszy gracz zaczyna
            currentPlayer = playerBoards.keySet().iterator().next();
            System.out.println("[BATTLESHIP GAME]: Game started! Current player: " + currentPlayer);
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

    //Setters
    public void setState(GameState state) {
        this.state = state;
    }
    public void setCurrentPlayer(int currentPlayer) {
        this.currentPlayer = currentPlayer;
    }

    // Nowa metoda do obsługi PLAYER_READY
    public boolean setPlayerReady(int playerId) {
        System.out.println("[BATTLESHIP GAME]: Setting player " + playerId + " as ready");

        if (state != GameState.SHIP_PLACEMENT) {
            System.out.println("[BATTLESHIP GAME]: Wrong state for setting ready: " + state);
            return false;
        }

        GameBoard board = playerBoards.get(playerId);
        if (board == null || !board.allShipsPlaced()) {
            System.out.println("[BATTLESHIP GAME]: Player " + playerId + " cannot be ready - ships not placed");
            return false;
        }

        playersReady.put(playerId, true);
        System.out.println("[BATTLESHIP GAME]: Player " + playerId + " is now ready");

        checkIfReadyToPlay();
        return true;
    }
}