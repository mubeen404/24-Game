package common;
import java.io.Serializable;

public class UserStatsRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private String username;
    public UserStatsRequest(String username) {
        this.username = username;
    }
    public String getUsername() {
        return username;
    }
} 