package app.flashcards;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class Sm2Test {
    final Instant now = Instant.parse("2026-11-04T10:00:00Z");

    @Test
    void goodReviewsGrowInterval2ThenTimesEase() {
        Card c = new Card();
        Sm2.review(c, 4, now);
        assertThat(c.getInterval()).isEqualTo(2);
        assertThat(c.getRepetitions()).isEqualTo(1);
        assertThat(c.getEaseFactor()).isEqualTo(2.5);
        assertThat(c.getDueAt()).isEqualTo(now.plus(Duration.ofDays(2)));

        Sm2.review(c, 4, now);
        assertThat(c.getInterval()).isEqualTo(5);

        Sm2.review(c, 4, now);
        assertThat(c.getInterval()).isEqualTo(13); // round(5 * 2.5)
        assertThat(c.getRepetitions()).isEqualTo(3);
    }

    @Test
    void gradesHaveDistinctIntervalsFromTheSameState() {
        // repetitions, interval, ease, Hard, Good, Easy (also mirrored in logic.test.ts)
        double[][] cases = {
                {0, 0, 2.5, 1, 2, 4},
                {0, 1, 1.3, 1, 2, 4}, // relearning after a failure
                {1, 1, 2.5, 2, 3, 4}, // rounding ties
                {1, 2, 2.5, 3, 5, 7},
                {2, 10, 2.5, 12, 25, 33},
                {5, 6, 1.3, 7, 8, 10}
        };
        for (double[] row : cases) {
            for (int quality = 0; quality <= 5; quality++) {
                Card c = new Card();
                c.setRepetitions((int) row[0]);
                c.setInterval((int) row[1]);
                c.setEaseFactor(row[2]);
                int expected = quality < 3 ? 1 : (int) row[quality];
                Sm2.review(c, quality, now);
                assertThat(c.getInterval()).isEqualTo(expected);
                assertThat(c.getDueAt()).isEqualTo(now.plus(Duration.ofDays(expected)));
                assertThat(c.getLastReviewedAt()).isEqualTo(now);
                assertThat(c.getRepetitions()).isEqualTo(quality < 3 ? 0 : (int) row[0] + 1);
                int d = 5 - quality;
                assertThat(c.getEaseFactor()).isCloseTo(
                        Math.max(1.3, row[2] + 0.1 - d * (0.08 + d * 0.02)),
                        org.assertj.core.data.Offset.offset(1e-9));
            }
        }
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
