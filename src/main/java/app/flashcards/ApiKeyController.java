package app.flashcards;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/** Long-lived keys for agents. Managing keys requires a Clerk session, so a key can't mint more keys. */
@RestController
@RequestMapping("/api/api-keys")
@RequiredArgsConstructor
class ApiKeyController {
    static final String PREFIX = "fc_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MongoTemplate mongo;

    record CreateRequest(String name) {}
    record Created(String id, String name, String prefix, String key) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Created create(@RequestBody CreateRequest r) {
        String userId = clerkUser();
        if (r.name() == null || r.name().isBlank() || r.name().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name must be 1-100 characters");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ApiKey k = new ApiKey();
        k.setUserId(userId);
        k.setName(r.name().strip());
        k.setKeyHash(hash(raw));
        k.setPrefix(raw.substring(0, 8));
        k.setCreatedAt(Instant.now());
        k = mongo.insert(k);
        return new Created(k.getId(), k.getName(), k.getPrefix(), raw);
    }

    @GetMapping
    List<ApiKey> list() {
        return mongo.find(query(where("userId").is(clerkUser())).with(Sort.by("createdAt")), ApiKey.class);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable String id) {
        if (mongo.remove(query(where("_id").is(id).and("userId").is(clerkUser())), ApiKey.class).getDeletedCount() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found");
        }
    }

    private static String clerkUser() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "API keys can only be managed from a signed-in session");
        }
        return SecurityConfig.userId();
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
