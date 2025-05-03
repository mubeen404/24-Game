package common;
import java.io.Serializable;

public class JoinGameRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private String username;

    public JoinGameRequest(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
} 