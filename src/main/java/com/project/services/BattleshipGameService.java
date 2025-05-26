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
        String gameId = UUID.randomUUID().toString();

        try {
            dao.connect();
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
                    "battleshipServerPort", getBattleshipServerPort()
            );
        } finally {
            dao.close();
        }
    }

    public int getBattleshipServerPort() {
        return Config.getBATTLESHIP_SERVER_PORT();
    }
}