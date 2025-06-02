package com.project.models.battleship.messages;

import com.project.models.battleship.BattleshipGame;

public class GameUpdateMessage extends BattleshipMessage {
    private static final long serialVersionUID = 1L;
    private final BattleshipGame game;

    public GameUpdateMessage(BattleshipGame game) {
        super(BattleshipMessageType.GAME_UPDATE, -1, game.getGameId());
        this.game = game;
    }

    public BattleshipGame getGame() { return game; }
}
