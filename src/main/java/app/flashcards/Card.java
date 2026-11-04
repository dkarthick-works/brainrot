package app.flashcards;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("cards")
@CompoundIndex(name = "user_due", def = "{'userId': 1, 'dueAt': 1}")
@CompoundIndex(name = "user_topic_question", def = "{'userId': 1, 'topicId': 1, 'questionKey': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
public class Card {
    @Id
    private String id;
    @JsonIgnore
    private String userId;
    private String topicId;
    private String question;
    private String answer;
    @JsonIgnore
    private String questionKey;

    // SM-2 state
    private double easeFactor = 2.5;
    private int interval;
    private int repetitions;
    private Instant dueAt;
    private Instant lastReviewedAt;

    private Instant createdAt;
    private Instant updatedAt;
}
