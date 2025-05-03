package common;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AnswerSubmission implements Serializable {
    private static final long serialVersionUID = 1L;
    private String username;
    private String expression;
    private long submitTime;
    private List<Integer> cardValues;

    public AnswerSubmission(String username, String expression, List<Integer> cardValues) {
        this.username = username;
        this.expression = expression;
        this.submitTime = System.currentTimeMillis(); // Automatically set current time
        // Create a new ArrayList to ensure it's serializable
        this.cardValues = new ArrayList<>(cardValues);
    }

    public AnswerSubmission(String username, String expression, long submitTime) {
        this.username = username;
        this.expression = expression;
        this.submitTime = submitTime;
        this.cardValues = new ArrayList<>();
    }

    public String getUsername() {
        return username;
    }

    public String getExpression() {
        return expression;
    }

    public long getSubmitTime() {
        return submitTime;
    }
    
    public List<Integer> getCardValues() {
        return cardValues;
    }
} 