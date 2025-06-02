package com.project.models.battleship.messages;

public enum BattleshipMessageType {
    JOIN_GAME,
    PLACE_SHIP,
    TAKE_SHOT,
    GAME_UPDATE,
    GAME_STATE_CHANGED,
    SHOT_RESULT,
    PLAYER_READY,
    SHIP_SUNK,
    ERROR
}