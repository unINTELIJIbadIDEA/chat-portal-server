package com.project.services;

import com.project.dao.BattleshipGameDAO;
import com.project.dao.ConversationDAO;
import com.project.models.battleship.BattleshipGameInfo;
import com.project.server.BattleshipServer;
import com.project.utils.Config;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BattleshipGameService {
    private final BattleshipGameDAO dao = new BattleshipGameDAO(
            Config.getDbUrl(),
            Config.getDbUsername(),
            Config.getDbPassword()
    );

    private final ConversationDAO conversationDAO = new ConversationDAO(
            Config.getDbUrl(),
            Config.getDbUsername(),
            Config.getDbPassword()
    );

    public String createGame(int creatorId, String gameName, String chatId) throws SQLException {
        try {
            dao.connect();

            // NOWA LOGIKA - sprawdź czy użytkownik już ma aktywną grę w tym czacie
            BattleshipGameInfo existingGame = getUserActiveGameInChat(creatorId, chatId);
            if (existingGame != null) {
                System.out.println("User " + creatorId + " already has active game: " + existingGame.getGameId());
                return existingGame.getGameId(); // Zwróć istniejącą grę
            }

            String gameId = UUID.randomUUID().toString();
            boolean created = dao.createGame(gameId, creatorId, gameName, chatId);
            if (created) {
                // Powiadom serwer Battleship o nowej grze
                BattleshipServer.getInstance().registerGame(gameId);
                return gameId;
            }
            return null;
        } finally {
            dao.close();
        }
    }

    public boolean joinChatGame(int playerId, String chatId) throws SQLException {
        try {
            dao.connect();

            // NOWA LOGIKA - sprawdź czy użytkownik już ma aktywną grę w tym czacie
            BattleshipGameInfo existingGame = getUserActiveGameInChat(playerId, chatId);
            if (existingGame != null) {
                System.out.println("User " + playerId + " already in game: " + existingGame.getGameId());
                return true; // Użytkownik już jest w grze
            }

            // Znajdź aktywną grę w czacie (WAITING status)
            BattleshipGameInfo gameInfo = dao.getActiveChatGame(chatId);

            if (gameInfo == null) {
                return false; // Brak dostępnych gier w czacie
            }

            if (gameInfo.getPlayer2Id() != null) {
                return false; // Gra już pełna
            }

            if (gameInfo.getPlayer1Id() == playerId) {
                return false; // Nie można dołączyć do własnej gry
            }

            boolean joined = dao.joinGame(gameInfo.getGameId(), playerId);
            if (joined) {
                // Powiadom serwer Battleship o nowym graczu
                BattleshipServer.getInstance().addPlayerToGame(gameInfo.getGameId(), playerId);
            }

            return joined;
        } finally {
            dao.close();
        }
    }

    // NOWA METODA - sprawdź czy użytkownik ma aktywną grę w czacie
    public BattleshipGameInfo getUserActiveGameInChat(int userId, String chatId) throws SQLException {
        try {
            dao.connect();
            List<BattleshipGameInfo> userGames = dao.getUserGames(userId);

            // Znajdź aktywną grę w tym czacie
            return userGames.stream()
                    .filter(game -> game.getChatId().equals(chatId))
                    .filter(game -> "WAITING".equals(game.getStatus()) ||
                            "READY".equals(game.getStatus()) ||
                            "PLAYING".equals(game.getStatus()))
                    .findFirst()
                    .orElse(null);
        } finally {
            dao.close();
        }
    }

    // NOWA METODA - opuść grę (gdy użytkownik się wylogowuje)
    public boolean leaveGame(int userId, String gameId) throws SQLException {
        try {
            dao.connect();
            BattleshipGameInfo gameInfo = dao.getGameInfo(gameId);

            if (gameInfo == null) return false;

            // Jeśli gracz 1 opuszcza grę
            if (gameInfo.getPlayer1Id() == userId) {
                if (gameInfo.getPlayer2Id() != null) {
                    // Drugi gracz zostaje graczem 1
                    return dao.updateGameAfterPlayerLeave(gameId, gameInfo.getPlayer2Id());
                } else {
                    // Usuń grę całkowicie
                    return dao.deleteGame(gameId);
                }
            }
            // Jeśli gracz 2 opuszcza grę
            else if (gameInfo.getPlayer2Id() != null && gameInfo.getPlayer2Id() == userId) {
                return dao.removePlayer2(gameId);
            }

            return false;
        } finally {
            dao.close();
        }
    }

    public boolean isUserInChat(int userId, String chatId) throws SQLException {
        try {
            conversationDAO.connect();
            List<String> userConversations = conversationDAO.getUserConversations(userId);
            return userConversations.contains(chatId);
        } finally {
            conversationDAO.close();
        }
    }

    public List<BattleshipGameInfo> getChatGames(String chatId) throws SQLException {
        try {
            dao.connect();
            return dao.getChatGames(chatId);
        } finally {
            dao.close();
        }
    }

    public BattleshipGameInfo getActiveChatGame(String chatId) throws SQLException {
        try {
            dao.connect();
            return dao.getActiveChatGame(chatId);
        } finally {
            dao.close();
        }
    }

    public List<BattleshipGameInfo> getUserGames(int userId) throws SQLException {
        try {
            dao.connect();
            return dao.getUserGames(userId);
        } finally {
            dao.close();
        }
    }

    public boolean updateGameStatus(String gameId, String status, Integer winnerId) throws SQLException {
        try {
            dao.connect();
            return dao.updateGameStatus(gameId, status, winnerId);
        } finally {
            dao.close();
        }
    }

    public Map<String, Object> getGameInfo(String gameId, int userId) throws SQLException {
        try {
            dao.connect();
            BattleshipGameInfo gameInfo = dao.getGameInfo(gameId);

            if (gameInfo == null) {
                return null;
            }

            // czy użytkownik należy do czatu tej gry
            if (!isUserInChat(userId, gameInfo.getChatId())) {
                return null; // Brak dostępu bo osoba nie należy do czatu
            }

            return Map.of(
                    "gameId", gameInfo.getGameId(),
                    "gameName", gameInfo.getGameName(),
                    "chatId", gameInfo.getChatId(),
                    "player1Id", gameInfo.getPlayer1Id(),
                    "player2Id", gameInfo.getPlayer2Id() != null ? gameInfo.getPlayer2Id() : -1,
                    "status", gameInfo.getStatus(),
                    "winnerId", gameInfo.getWinnerId() != null ? gameInfo.getWinnerId() : -1,
                    "createdAt", gameInfo.getCreatedAt().toString(),
                    "battleshipServerPort", getBattleshipServerPort(),
                    "isUserInGame", isUserInGame(gameInfo, userId)
            );
        } finally {
            dao.close();
        }
    }

    // NOWA METODA - sprawdź czy użytkownik jest w grze
    private boolean isUserInGame(BattleshipGameInfo gameInfo, int userId) {
        return gameInfo.getPlayer1Id() == userId ||
                (gameInfo.getPlayer2Id() != null && gameInfo.getPlayer2Id() == userId);
    }

    public int getBattleshipServerPort() {
        return Config.getBATTLESHIP_SERVER_PORT();
    }
}