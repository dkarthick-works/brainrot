package app.flashcards;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class Sm2Test {
    final Instant now = Instant.parse("2026-11-04T10:00:00Z");

    @Test
    void successfulReviewsGrowInterval1Then6ThenTimesEase() {
        Card c = new Card();
        Sm2.review(c, 4, now);
        assertThat(c.getInterval()).isEqualTo(1);
        assertThat(c.getRepetitions()).isEqualTo(1);
        assertThat(c.getEaseFactor()).isEqualTo(2.5);
        assertThat(c.getDueAt()).isEqualTo(now.plus(Duration.ofDays(1)));

        Sm2.review(c, 4, now);
        assertThat(c.getInterval()).isEqualTo(6);

        Sm2.review(c, 4, now);
        assertThat(c.getInterval()).isEqualTo(15); // round(6 * 2.5)
        assertThat(c.getRepetitions()).isEqualTo(3);
    }

    @Test
    void easyRaisesEaseAndFailureResets() {
        Card c = new Card();
        Sm2.review(c, 5, now);
        assertThat(c.getEaseFactor()).isCloseTo(2.6, org.assertj.core.data.Offset.offset(1e-9));

        Sm2.review(c, 4, now);
        Sm2.review(c, 1, now);
        assertThat(c.getRepetitions()).isZero();
        assertThat(c.getInterval()).isEqualTo(1);
        assertThat(c.getEaseFactor()).isCloseTo(2.6 - 0.54, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void easeNeverDropsBelow1_3() {
        Card c = new Card();
        for (int i = 0; i < 10; i++) Sm2.review(c, 0, now);
        assertThat(c.getEaseFactor()).isEqualTo(1.3);
    }
}
