package model;

import java.io.Serializable;

public class UserStats implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String username;
    private int gamesPlayed;
    private int gamesWon;
    private double avgTimeToWin; // in seconds
    private int rank;

    public UserStats(String username) {
        this.username = username;
        this.gamesPlayed = 0;
        this.gamesWon = 0;
        this.avgTimeToWin = 0.0;
        this.rank = 0;
    }

    public UserStats(String username, int gamesPlayed, int gamesWon, double avgTimeToWin, int rank) {
        this.username = username;
        this.gamesPlayed = gamesPlayed;
        this.gamesWon = gamesWon;
        this.avgTimeToWin = avgTimeToWin;
        this.rank = rank;
    }

    // Getters
    public String getUsername() { return username; }
    public int getGamesPlayed() { return gamesPlayed; }
    public int getGamesWon() { return gamesWon; }
    public double getAvgTimeToWin() { return avgTimeToWin; }
    public int getRank() { return rank; }

    // Setters
    public void setRank(int rank) {
        this.rank = rank;
    }

    @Override
    public String toString() {
        return String.format("%s:%d:%d:%.2f:%d", 
            username, gamesPlayed, gamesWon, avgTimeToWin, rank);
    }
} 