package com.project.services;

import com.project.dao.BattleshipGameDAO;
import com.project.dao.ConversationDAO;
import com.project.dao.UsersDAO;
import com.project.models.User;
import com.project.models.battleship.BattleshipGameInfo;
import com.project.server.BattleshipServer;
import com.project.config.ConfigProperties;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BattleshipGameService {
    private final BattleshipGameDAO dao = new BattleshipGameDAO(
            ConfigProperties.getDbUrl(),
            ConfigProperties.getDbUsername(),
            ConfigProperties.getDbPassword()
    );

    private final ConversationDAO conversationDAO = new ConversationDAO(
            ConfigProperties.getDbUrl(),
            ConfigProperties.getDbUsername(),
            ConfigProperties.getDbPassword()
    );

    public String createGame(int creatorId, String gameName, String chatId) throws SQLException {
        System.out.println("=== CREATE GAME DEBUG ===");
        System.out.println("Creator ID: " + creatorId);
        System.out.println("Game Name: " + gameName);
        System.out.println("Chat ID: " + chatId);

        try {
            // Połącz z bazą
            System.out.println("Connecting to database...");
            dao.connect();
            System.out.println("Database connected successfully");

            // Sprawdź czy użytkownik już ma aktywną grę w tym czacie
            System.out.println("Checking for existing games...");
            BattleshipGameInfo existingGame = getUserActiveGameInChatInternal(creatorId, chatId);
            if (existingGame != null) {
                System.out.println("User " + creatorId + " already has active game: " + existingGame.getGameId());
                BattleshipServer.getInstance().registerGame(existingGame.getGameId());
                return existingGame.getGameId();
            }

            String gameId = UUID.randomUUID().toString();
            System.out.println("Creating new game with ID: " + gameId);

            boolean created = dao.createGame(gameId, creatorId, gameName, chatId);
            if (created) {
                System.out.println("Game created successfully in database");

                // KRYTYCZNE: Zarejestruj grę na serwerze Battleship
                BattleshipServer.getInstance().registerGame(gameId);
                System.out.println("Game registered with Battleship server");

                return gameId;
            } else {
                System.err.println("Failed to create game in database");
                return null;
            }
        } catch (SQLException e) {
            System.err.println("=== DATABASE ERROR ===");
            System.err.println("Error: " + e.getMessage());
            System.err.println("SQLState: " + e.getSQLState());
            System.err.println("Error Code: " + e.getErrorCode());
            e.printStackTrace();
            throw e;
        } finally {
            try {
                dao.close();
                System.out.println("Database connection closed");
            } catch (SQLException e) {
                System.err.println("Error closing database connection: " + e.getMessage());
            }
        }
    }


    private BattleshipGameInfo getUserActiveGameInChatInternal(int userId, String chatId) throws SQLException {
        System.out.println("Checking active games for user " + userId + " in chat " + chatId);
        try {

            List<BattleshipGameInfo> userGames = dao.getUserGames(userId);
            System.out.println("Found " + userGames.size() + " games for user");

            BattleshipGameInfo activeGame = userGames.stream()
                    .filter(game -> game.getChatId().equals(chatId))
                    .filter(game -> "WAITING".equals(game.getStatus()) ||
                            "READY".equals(game.getStatus()) ||
                            "PLAYING".equals(game.getStatus()) ||
                            "PAUSED".equals(game.getStatus()))
                    .findFirst()
                    .orElse(null);

            if (activeGame != null) {
                System.out.println("Found active game: " + activeGame.getGameId());
            } else {
                System.out.println("No active games found");
            }

            return activeGame;
        } catch (SQLException e) {
            System.err.println("Error checking active games: " + e.getMessage());
            throw e;
        }
    }
    public BattleshipGameInfo getUserActiveGameInChat(int userId, String chatId) throws SQLException {
        try {
            dao.connect();
            return getUserActiveGameInChatInternal(userId, chatId);
        } finally {
            dao.close();
        }
    }

    public boolean joinChatGame(int playerId, String chatId) throws SQLException {
        try {
            dao.connect();
            System.out.println("[BATTLESHIP SERVICE]: Join request - Player: " + playerId + ", Chat: " + chatId);

            BattleshipGameInfo existingGame = getUserActiveGameInChatInternal(playerId, chatId);
            if (existingGame != null) {
                System.out.println("[BATTLESHIP SERVICE]: User " + playerId + " already in game: " + existingGame.getGameId());

                BattleshipServer.getInstance().registerGame(existingGame.getGameId());
                return true;
            }
            System.out.println("[BATTLESHIP SERVICE]: Looking for active games in chat: " + chatId);
            List<BattleshipGameInfo> chatGames = dao.getChatGames(chatId);
            System.out.println("[BATTLESHIP SERVICE]: Found " + chatGames.size() + " games in chat");

            BattleshipGameInfo availableGame = null;
            for (BattleshipGameInfo game : chatGames) {
                System.out.println("[BATTLESHIP SERVICE]: Game " + game.getGameId() +
                        " - Status: " + game.getStatus() +
                        " - Player1: " + game.getPlayer1Id() +
                        " - Player2: " + game.getPlayer2Id());
                if ("WAITING".equals(game.getStatus()) && game.getPlayer2Id() == null) {
                    if (game.getPlayer1Id() != playerId) {
                        availableGame = game;
                        break;
                    }
                }
            }

            if (availableGame == null) {
                System.out.println("[BATTLESHIP SERVICE]: No available games found in chat");
                return false;
            }

            System.out.println("[BATTLESHIP SERVICE]: Found available game: " + availableGame.getGameId());

            // Dołącz do gry
            boolean joined = dao.joinGame(availableGame.getGameId(), playerId);
            if (joined) {
                System.out.println("[BATTLESHIP SERVICE]: Successfully joined game in database");

                // KRYTYCZNE: Zarejestruj grę na serwerze Battleship
                BattleshipServer.getInstance().registerGame(availableGame.getGameId());
                System.out.println("[BATTLESHIP SERVICE]: Game registered with Battleship server");
            } else {
                System.err.println("[BATTLESHIP SERVICE]: Failed to join game in database");
            }

            return joined;
        } finally {
            dao.close();
        }
    }

    public boolean leaveGame(int userId, String gameId) throws SQLException {
        try {
            dao.connect();
            BattleshipGameInfo gameInfo = dao.getGameInfo(gameId);

            if (gameInfo == null) return false;


            if (gameInfo.getPlayer1Id() == userId) {
                if (gameInfo.getPlayer2Id() != null) {

                    return dao.updateGameAfterPlayerLeave(gameId, gameInfo.getPlayer2Id());
                } else {
                    return dao.deleteGame(gameId);
                }
            }
            else if (gameInfo.getPlayer2Id() != null && gameInfo.getPlayer2Id() == userId) {
                return dao.removePlayer2(gameId);
            }

            return false;
        } finally {
            dao.close();
        }
    }

    public boolean isUserInChat(int userId, String chatId) throws SQLException {
        System.out.println("Checking if user " + userId + " is in chat " + chatId);
        try {
            conversationDAO.connect();
            List<String> userConversations = conversationDAO.getUserConversations(userId);
            boolean isInChat = userConversations.contains(chatId);
            System.out.println("User in chat: " + isInChat);
            return isInChat;
        } catch (SQLException e) {
            System.err.println("Error checking user chat membership: " + e.getMessage());
            throw e;
        } finally {
            try {
                conversationDAO.close();
            } catch (SQLException e) {
                System.err.println("Error closing conversation DAO: " + e.getMessage());
            }
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
            System.out.println("[BATTLESHIP SERVICE]: Getting active game for chat: " + chatId);

            List<BattleshipGameInfo> chatGames = dao.getChatGames(chatId);
            System.out.println("[BATTLESHIP SERVICE]: Found " + chatGames.size() + " games in chat");
            BattleshipGameInfo activeGame = chatGames.stream()
                    .filter(game -> "WAITING".equals(game.getStatus()) ||
                            "READY".equals(game.getStatus()) ||
                            "PLAYING".equals(game.getStatus()))
                    .findFirst()
                    .orElse(null);

            if (activeGame != null) {
                System.out.println("[BATTLESHIP SERVICE]: Found active game: " + activeGame.getGameId() +
                        " with status: " + activeGame.getStatus());
            } else {
                System.out.println("[BATTLESHIP SERVICE]: No active games found");
            }

            return activeGame;
        } finally {
            dao.close();
        }
    }

    public BattleshipGameInfo getGameInfoDirect(String gameId) throws SQLException {
        try {
            dao.connect();
            return dao.getGameInfo(gameId);
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

            if (!isUserInChat(userId, gameInfo.getChatId())) {
                return null;
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

    private boolean isUserInGame(BattleshipGameInfo gameInfo, int userId) {
        return gameInfo.getPlayer1Id() == userId ||
                (gameInfo.getPlayer2Id() != null && gameInfo.getPlayer2Id() == userId);
    }

    public int getBattleshipServerPort() {
        return ConfigProperties.getBATTLESHIP_SERVER_PORT();
    }

    public boolean pauseGame(String gameId) throws SQLException {
        try {
            dao.connect();
            return dao.pauseGame(gameId);
        } finally {
            dao.close();
        }
    }

    public boolean resumeGame(String gameId) throws SQLException {
        try {
            dao.connect();
            return dao.resumeGame(gameId);
        } finally {
            dao.close();
        }
    }

    public List<BattleshipGameInfo> getPausedGamesForUser(int userId) throws SQLException {
        try {
            dao.connect();
            return dao.getPausedGamesForUser(userId);
        } finally {
            dao.close();
        }
    }

    public String getUserNickname(int userId) throws SQLException {
        UsersDAO usersDAO = new UsersDAO(
                ConfigProperties.getDbUrl(),
                ConfigProperties.getDbUsername(),
                ConfigProperties.getDbPassword()
        );

        try {
            usersDAO.connect();
            User user = usersDAO.getUserWithId(userId);
            return user != null ? user.getNickname() : "Gracz " + userId;
        } finally {
            usersDAO.close();
        }
    }
}