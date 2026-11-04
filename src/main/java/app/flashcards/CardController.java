package app.flashcards;

import app.flashcards.CardService.CreateResult;
import app.flashcards.CardService.Due;
import app.flashcards.CardService.MoveResult;
import app.flashcards.CardService.NewCard;
import app.flashcards.CardService.Page;
import app.flashcards.CardService.TopicView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static app.flashcards.SecurityConfig.userId;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
class CardController {
    private final CardService svc;

    record CreateRequest(String topicId, List<NewCard> cards) {}
    record ReviewRequest(Integer quality) {}
    record CardPatch(String question, String answer, String topicId) {}
    record NameRequest(String name) {}
    record MoveRequest(String toTopicId) {}

    @GetMapping("/cards/due")
    Due due(@RequestParam(required = false) String topicId, @RequestParam(defaultValue = "20") int limit) {
        return svc.due(userId(), topicId, limit);
    }

    @PostMapping("/cards/{id}/review")
    Card review(@PathVariable String id, @RequestBody ReviewRequest r) {
        return svc.review(userId(), id, r.quality());
    }

    @PostMapping("/cards")
    CreateResult create(@RequestBody CreateRequest r) {
        return svc.create(userId(), r.topicId(), r.cards());
    }

    @GetMapping("/cards")
    Page list(@RequestParam(required = false) String topicId, @RequestParam(required = false) String q,
              @RequestParam(required = false) String after, @RequestParam(defaultValue = "50") int limit) {
        return svc.list(userId(), topicId, q, after, limit);
    }

    @PatchMapping("/cards/{id}")
    Card update(@PathVariable String id, @RequestBody CardPatch p) {
        return svc.update(userId(), id, p.question(), p.answer(), p.topicId());
    }

    @DeleteMapping("/cards/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        svc.delete(userId(), id);
    }

    @GetMapping("/topics")
    List<TopicView> topics() {
        return svc.topics(userId());
    }

    @PostMapping("/topics")
    @ResponseStatus(HttpStatus.CREATED)
    Topic createTopic(@RequestBody NameRequest r) {
        return svc.createTopic(userId(), r.name());
    }

    @PatchMapping("/topics/{id}")
    Topic renameTopic(@PathVariable String id, @RequestBody NameRequest r) {
        return svc.renameTopic(userId(), id, r.name());
    }

    @DeleteMapping("/topics/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTopic(@PathVariable String id) {
        svc.deleteTopic(userId(), id);
    }

    @PostMapping("/topics/{id}/move-cards")
    MoveResult moveCards(@PathVariable String id, @RequestBody MoveRequest r) {
        return svc.moveCards(userId(), id, r.toTopicId());
    }
}
