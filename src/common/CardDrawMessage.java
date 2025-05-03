package common;
import java.io.Serializable;
import java.util.List;

public class CardDrawMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<Integer> cards;

    public CardDrawMessage(List<Integer> cards) {
        this.cards = new java.util.ArrayList<>(cards);
    }

    public List<Integer> getCards() {
        return cards;
    }
} 