package app.flashcards;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("topics")
@CompoundIndex(name = "user_name", def = "{'userId': 1, 'nameKey': 1}", unique = true)
public record Topic(@Id String id, @JsonIgnore String userId, String name, @JsonIgnore String nameKey, Instant createdAt) {
}
