package com.project.dao;
import com.project.models.battleship.BattleshipGameInfo;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BattleshipGameDAO {
    private final String dbURL;
    private final String dbUser;
    private final String dbPassword;
    private Connection connection;

    public BattleshipGameDAO(String dbURL, String dbUser, String dbPassword) {
        this.dbURL = dbURL;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public void connect() throws SQLException {
        connection = DriverManager.getConnection(dbURL, dbUser, dbPassword);
    }

    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    public boolean createGame(String gameId, int creatorId, String gameName, String chatId) throws SQLException {
        String sql = "INSERT INTO battleship_games (game_id, game_name, chat_id, player1_id, status, created_at) VALUES (?, ?, ?, ?, 'WAITING', NOW())";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, gameId);
            stmt.setString(2, gameName);
            stmt.setString(3, chatId);
            stmt.setInt(4, creatorId);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean joinGame(String gameId, int playerId) throws SQLException {
        String sql = "UPDATE battleship_games SET player2_id = ?, status = 'READY' WHERE game_id = ? AND player2_id IS NULL";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            stmt.setString(2, gameId);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean updateGameStatus(String gameId, String status, Integer winnerId) throws SQLException {
        String sql = "UPDATE battleship_games SET status = ?, winner_id = ? WHERE game_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status);
            if (winnerId != null) {
                stmt.setInt(2, winnerId);
            } else {
                stmt.setNull(2, Types.INTEGER);
            }
            stmt.setString(3, gameId);
            return stmt.executeUpdate() > 0;
        }
    }

    public BattleshipGameInfo getGameInfo(String gameId) throws SQLException {
        String sql = "SELECT * FROM battleship_games WHERE game_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, gameId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToGameInfo(rs);
                }
            }
        }
        return null;
    }

    public List<BattleshipGameInfo> getChatGames(String chatId) throws SQLException {
        String sql = "SELECT * FROM battleship_games WHERE chat_id = ? ORDER BY created_at DESC";
        List<BattleshipGameInfo> games = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, chatId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    games.add(mapResultSetToGameInfo(rs));
                }
            }
        }
        return games;
    }

    public BattleshipGameInfo getActiveChatGame(String chatId) throws SQLException {
        String sql = "SELECT * FROM battleship_games WHERE chat_id = ? AND status = 'WAITING' ORDER BY created_at DESC LIMIT 1";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, chatId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToGameInfo(rs);
                }
            }
        }
        return null;
    }

    public List<BattleshipGameInfo> getUserGames(int userId) throws SQLException {
        String sql = "SELECT * FROM battleship_games WHERE player1_id = ? OR player2_id = ? ORDER BY created_at DESC";
        List<BattleshipGameInfo> games = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    games.add(mapResultSetToGameInfo(rs));
                }
            }
        }
        return games;
    }

    private BattleshipGameInfo mapResultSetToGameInfo(ResultSet rs) throws SQLException {
        return new BattleshipGameInfo(
                rs.getString("game_id"),
                rs.getString("game_name"),
                rs.getString("chat_id"),
                rs.getInt("player1_id"),
                rs.getObject("player2_id") != null ? rs.getInt("player2_id") : null,
                rs.getString("status"),
                rs.getObject("winner_id") != null ? rs.getInt("winner_id") : null,
                rs.getTimestamp("created_at")
        );
    }

    // DODAJ DO ISTNIEJĄCEJ KLASY BattleshipGameDAO

    public boolean updateGameAfterPlayerLeave(String gameId, int newPlayer1Id) throws SQLException {
        String sql = "UPDATE battleship_games SET player1_id = ?, player2_id = NULL, status = 'WAITING' WHERE game_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, newPlayer1Id);
            stmt.setString(2, gameId);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean removePlayer2(String gameId) throws SQLException {
        String sql = "UPDATE battleship_games SET player2_id = NULL, status = 'WAITING' WHERE game_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, gameId);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean deleteGame(String gameId) throws SQLException {
        String sql = "DELETE FROM battleship_games WHERE game_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, gameId);
            return stmt.executeUpdate() > 0;
        }
    }

}