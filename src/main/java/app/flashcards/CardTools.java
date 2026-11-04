package app.flashcards;

import app.flashcards.CardService.Due;
import app.flashcards.CardService.NewCard;
import app.flashcards.CardService.Page;
import app.flashcards.CardService.Skipped;
import app.flashcards.CardService.TopicView;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

import static app.flashcards.SecurityConfig.userId;

/** MCP tools for agents. Same CardService as the REST API; the user comes from the request's API key. */
@Component
@RequiredArgsConstructor
public class CardTools {
    private final CardService svc;

    public record CreateSummary(int created, List<Skipped> skipped) {}
    public record Deleted(String id, boolean deleted) {}

    @McpTool(name = "create_flashcards", description = """
            Create flashcards (question + answer) for the user, up to 100 per call. \
            Keep each card to one fact; questions should make sense on their own. \
            A question that already exists in the same topic (ignoring case and spacing) is skipped and reported.""")
    public CreateSummary createFlashcards(
            @McpToolParam(description = "Cards to create") List<NewCard> cards,
            @McpToolParam(description = "Topic name. Created if it doesn't exist. Omit to use the user's Default topic.",
                    required = false) String topic) {
        String uid = userId();
        var r = svc.create(uid, svc.topicIdByName(uid, topic, true), cards);
        return new CreateSummary(r.created().size(), r.skipped());
    }

    @McpTool(name = "search_flashcards", description = """
            Search the user's flashcards by question or answer text (plain substring, case-insensitive), newest first. \
            Use it to check for existing cards before creating new ones. Pass nextCursor back as 'after' for more.""",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public Page searchFlashcards(
            @McpToolParam(description = "Text to look for in questions and answers. Omit to list all.", required = false) String query,
            @McpToolParam(description = "Only this topic (by name).", required = false) String topic,
            @McpToolParam(description = "Max results, 1-100. Default 20.", required = false) Integer limit,
            @McpToolParam(description = "nextCursor from a previous call.", required = false) String after) {
        String uid = userId();
        String topicId = topic == null || topic.isBlank() ? null : svc.topicIdByName(uid, topic, false);
        return svc.list(uid, topicId, query, after, limit == null ? 20 : limit);
    }

    @McpTool(name = "update_flashcard", description = "Edit a flashcard's question, answer, or topic. Review history is kept.")
    public Card updateFlashcard(
            @McpToolParam(description = "Card id") String id,
            @McpToolParam(description = "New question", required = false) String question,
            @McpToolParam(description = "New answer", required = false) String answer,
            @McpToolParam(description = "Move to this topic (by name; created if missing).", required = false) String topic) {
        String uid = userId();
        String topicId = topic == null || topic.isBlank() ? null : svc.topicIdByName(uid, topic, true);
        return svc.update(uid, id, question, answer, topicId);
    }

    @McpTool(name = "delete_flashcard", description = "Permanently delete a flashcard.")
    public Deleted deleteFlashcard(@McpToolParam(description = "Card id") String id) {
        svc.delete(userId(), id);
        return new Deleted(id, true);
    }

    @McpTool(name = "get_due_flashcards", description = """
            Get flashcards due for review now, oldest due first, plus the total due count. \
            Use with review_flashcard to quiz the user.""",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public Due getDueFlashcards(
            @McpToolParam(description = "Max cards, 1-100. Default 20.", required = false) Integer limit,
            @McpToolParam(description = "Only this topic (by name).", required = false) String topic) {
        String uid = userId();
        String topicId = topic == null || topic.isBlank() ? null : svc.topicIdByName(uid, topic, false);
        return svc.due(uid, topicId, limit == null ? 20 : limit);
    }

    @McpTool(name = "review_flashcard", description = """
            Record how well the user recalled a card (SM-2) and schedule its next review. \
            Quality: 0-2 = forgot (card resets), 3 = hard, 4 = good, 5 = easy.""")
    public Card reviewFlashcard(
            @McpToolParam(description = "Card id") String id,
            @McpToolParam(description = "Recall quality 0-5") Integer quality) {
        return svc.review(userId(), id, quality);
    }

    @McpTool(name = "list_topics", description = "List the user's topics with card and due counts. Reuse these names when creating cards.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<TopicView> listTopics() {
        return svc.topics(userId());
    }
}
