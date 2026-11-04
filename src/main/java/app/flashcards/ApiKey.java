package app.flashcards;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("api_keys")
@Getter
@Setter
@NoArgsConstructor
public class ApiKey {
    @Id
    private String id;
    @Indexed
    @JsonIgnore
    private String userId;
    private String name;
    @Indexed(unique = true)
    @JsonIgnore
    private String keyHash;
    private String prefix;
    private Instant createdAt;
    private Instant lastUsedAt;
}
