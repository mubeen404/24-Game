package common;
import java.io.Serializable;
import model.UserStats;

public class UserStatsResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    private UserStats userStats;
    public UserStatsResponse(UserStats userStats) {
        this.userStats = userStats;
    }
    public UserStats getUserStats() {
        return userStats;
    }
} 