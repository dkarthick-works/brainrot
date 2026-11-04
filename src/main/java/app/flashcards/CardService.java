package app.flashcards;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/** Cards and topics. Shared by the REST controllers and the MCP tools; validates all input itself. */
@Service
@RequiredArgsConstructor
public class CardService {
    static final int MAX_BATCH = 100;

    private final MongoTemplate mongo;

    public record NewCard(String question, String answer) {}
    public record Skipped(String id, String question, String reason) {}
    public record CreateResult(List<Card> created, List<Skipped> skipped) {}
    public record Due(List<Card> cards, long dueCount, Instant nextDueAt) {}
    public record Page(List<Card> cards, String nextCursor) {}
    public record TopicView(String id, String name, boolean isDefault, long cardCount, long dueCount) {}
    public record MoveResult(int moved, List<Skipped> skipped) {}

    // ---- cards ----

    public CreateResult create(String userId, String topicId, List<NewCard> cards) {
        if (cards == null || cards.isEmpty()) throw bad("cards must not be empty");
        if (cards.size() > MAX_BATCH) throw bad("at most " + MAX_BATCH + " cards per request");
        String topic = topicId == null ? ensureDefaultTopic(userId).id() : requireTopic(userId, topicId).id();
        Instant now = Instant.now();
        List<Card> created = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        // ponytail: one insert per card so duplicates are skipped individually; fine at <=100 per batch
        for (NewCard n : cards) {
            if (n == null) throw bad("card must not be null");
            Card c = new Card();
            c.setUserId(userId);
            c.setTopicId(topic);
            c.setQuestion(text(n.question(), 2000, "question"));
            c.setAnswer(text(n.answer(), 5000, "answer"));
            c.setQuestionKey(key(c.getQuestion()));
            c.setDueAt(now);
            c.setCreatedAt(now);
            c.setUpdatedAt(now);
            try {
                created.add(mongo.insert(c));
            } catch (DuplicateKeyException e) {
                skipped.add(new Skipped(null, c.getQuestion(), "duplicate"));
            }
        }
        return new CreateResult(created, skipped);
    }

    public Due due(String userId, String topicId, int limit) {
        Instant now = Instant.now();
        Criteria owner = where("userId").is(userId);
        if (topicId != null) owner = owner.and("topicId").is(topicId);
        Criteria c = new Criteria().andOperator(owner, where("dueAt").lte(now));
        long count = mongo.count(query(c), Card.class);
        List<Card> cards = mongo.find(query(c).with(Sort.by("dueAt")).limit(Math.clamp(limit, 1, 100)), Card.class);
        // soonest card not yet due, for "Next card due in 3h"
        Card next = mongo.findOne(query(new Criteria().andOperator(owner, where("dueAt").gt(now))).with(Sort.by("dueAt")), Card.class);
        return new Due(cards, count, next == null ? null : next.getDueAt());
    }

    public Page list(String userId, String topicId, String q, String after, int limit) {
        limit = Math.clamp(limit, 1, 100);
        Criteria c = where("userId").is(userId);
        if (topicId != null) c = c.and("topicId").is(topicId);
        if (q != null && !q.isBlank()) {
            String re = Pattern.quote(q.trim());
            c = c.orOperator(where("question").regex(re, "i"), where("answer").regex(re, "i"));
        }
        if (after != null) {
            if (!ObjectId.isValid(after)) throw bad("invalid cursor");
            c = c.and("_id").lt(new ObjectId(after));
        }
        List<Card> cards = mongo.find(query(c).with(Sort.by(Sort.Direction.DESC, "_id")).limit(limit + 1), Card.class);
        if (cards.size() <= limit) return new Page(cards, null);
        cards = cards.subList(0, limit);
        return new Page(cards, cards.getLast().getId());
    }

    public Card review(String userId, String id, Integer quality) {
        if (quality == null || quality < 0 || quality > 5) throw bad("quality must be 0-5");
        Card c = card(userId, id);
        Instant now = Instant.now();
        Sm2.review(c, quality, now);
        c.setUpdatedAt(now);
        return mongo.save(c);
    }

    public Card update(String userId, String id, String question, String answer, String topicId) {
        Card c = card(userId, id);
        if (question != null) {
            c.setQuestion(text(question, 2000, "question"));
            c.setQuestionKey(key(c.getQuestion()));
        }
        if (answer != null) c.setAnswer(text(answer, 5000, "answer"));
        if (topicId != null) c.setTopicId(requireTopic(userId, topicId).id());
        c.setUpdatedAt(Instant.now());
        try {
            return mongo.save(c);
        } catch (DuplicateKeyException e) {
            throw conflict("The target topic already has this question");
        }
    }

    public void delete(String userId, String id) {
        if (mongo.remove(byIdAndUser(id, userId), Card.class).getDeletedCount() == 0) throw notFound("Card");
    }

    // ---- topics ----

    public List<TopicView> topics(String userId) {
        String defaultId = ensureDefaultTopic(userId).id();
        var dueNow = ComparisonOperators.valueOf("dueAt").lessThanEqualToValue(Instant.now());
        var agg = newAggregation(
                match(where("userId").is(userId)),
                group("topicId").count().as("cardCount")
                        .sum(ConditionalOperators.when(dueNow).then(1).otherwise(0)).as("dueCount"));
        Map<String, Document> counts = new HashMap<>();
        mongo.aggregate(agg, Card.class, Document.class).forEach(d -> counts.put(d.getString("_id"), d));
        return mongo.find(query(where("userId").is(userId)).with(Sort.by("createdAt")), Topic.class).stream()
                .map(t -> {
                    Document d = counts.get(t.id());
                    return new TopicView(t.id(), t.name(), t.id().equals(defaultId),
                            d == null ? 0 : ((Number) d.get("cardCount")).longValue(),
                            d == null ? 0 : ((Number) d.get("dueCount")).longValue());
                })
                .toList();
    }

    public Topic createTopic(String userId, String name) {
        ensureDefaultTopic(userId);
        name = text(name, 100, "name");
        try {
            return mongo.insert(new Topic(new ObjectId().toHexString(), userId, name, key(name), Instant.now()));
        } catch (DuplicateKeyException e) {
            throw conflict("Topic '" + name + "' already exists");
        }
    }

    public Topic renameTopic(String userId, String id, String name) {
        Topic t = requireTopic(userId, id);
        name = text(name, 100, "name");
        try {
            return mongo.save(new Topic(t.id(), userId, name, key(name), t.createdAt()));
        } catch (DuplicateKeyException e) {
            throw conflict("Topic '" + name + "' already exists");
        }
    }

    public void deleteTopic(String userId, String id) {
        if (id.equals(defaultTopicId(userId))) throw conflict("The default topic can't be deleted");
        requireTopic(userId, id);
        long n = mongo.count(query(where("userId").is(userId).and("topicId").is(id)), Card.class);
        if (n > 0) throw conflict("Topic has " + n + " cards. Move or delete them first.");
        // ponytail: check-then-delete race with a concurrent card insert is accepted at hobby scale
        mongo.remove(byIdAndUser(id, userId), Topic.class);
    }

    public MoveResult moveCards(String userId, String fromId, String toId) {
        requireTopic(userId, fromId);
        if (toId == null) throw bad("toTopicId is required");
        requireTopic(userId, toId);
        if (fromId.equals(toId)) throw bad("Source and target topic are the same");
        int moved = 0;
        List<Skipped> skipped = new ArrayList<>();
        // ponytail: loads the whole topic and saves card by card so duplicates are skipped; fine for thousands of cards
        for (Card c : mongo.find(query(where("userId").is(userId).and("topicId").is(fromId)), Card.class)) {
            c.setTopicId(toId);
            c.setUpdatedAt(Instant.now());
            try {
                mongo.save(c);
                moved++;
            } catch (DuplicateKeyException e) {
                skipped.add(new Skipped(c.getId(), c.getQuestion(), "duplicate"));
            }
        }
        return new MoveResult(moved, skipped);
    }

    /** For agents: topics are addressed by name and created on first use. Null/blank means Default. */
    public String topicIdByName(String userId, String name, boolean create) {
        Topic def = ensureDefaultTopic(userId);
        if (name == null || name.isBlank()) return def.id();
        Topic t = mongo.findOne(query(where("userId").is(userId).and("nameKey").is(key(name))), Topic.class);
        if (t != null) return t.id();
        if (!create) throw notFound("Topic '" + name.trim() + "'");
        try {
            return createTopic(userId, name).id();
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() != HttpStatus.CONFLICT) throw e;
            return topicIdByName(userId, name, false); // created concurrently
        }
    }

    Topic ensureDefaultTopic(String userId) {
        String id = defaultTopicId(userId);
        Topic t = mongo.findById(id, Topic.class);
        if (t != null) return t;
        try {
            return mongo.insert(new Topic(id, userId, "Default", "default", Instant.now()));
        } catch (DuplicateKeyException e) {
            return mongo.findById(id, Topic.class);
        }
    }

    // ---- helpers ----

    static String defaultTopicId(String userId) {
        return userId + ":default";
    }

    private Topic requireTopic(String userId, String id) {
        if (id.equals(defaultTopicId(userId))) return ensureDefaultTopic(userId);
        Topic t = mongo.findOne(byIdAndUser(id, userId), Topic.class);
        if (t == null) throw notFound("Topic");
        return t;
    }

    private Card card(String userId, String id) {
        Card c = mongo.findOne(byIdAndUser(id, userId), Card.class);
        if (c == null) throw notFound("Card");
        return c;
    }

    private static Query byIdAndUser(String id, String userId) {
        return query(where("_id").is(id).and("userId").is(userId));
    }

    static String key(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String text(String s, int max, String field) {
        if (s == null || s.isBlank()) throw bad(field + " must not be blank");
        s = s.strip();
        if (s.length() > max) throw bad(field + " must be at most " + max + " characters");
        return s;
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static ResponseStatusException conflict(String msg) {
        return new ResponseStatusException(HttpStatus.CONFLICT, msg);
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found");
    }
}
