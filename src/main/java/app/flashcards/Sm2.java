package app.flashcards;

import java.time.Duration;
import java.time.Instant;

/** SuperMemo 2. Quality 0-5; UI buttons map Again=1, Hard=3, Good=4, Easy=5. */
final class Sm2 {
    private Sm2() {}

    static void review(Card c, int quality, Instant now) {
        if (quality < 0 || quality > 5) throw new IllegalArgumentException("quality must be 0-5");
        if (quality >= 3) {
            c.setInterval(switch (c.getRepetitions()) {
                case 0 -> 1;
                case 1 -> 6;
                default -> (int) Math.round(c.getInterval() * c.getEaseFactor());
            });
            c.setRepetitions(c.getRepetitions() + 1);
        } else {
            c.setRepetitions(0);
            c.setInterval(1);
        }
        int d = 5 - quality;
        c.setEaseFactor(Math.max(1.3, c.getEaseFactor() + 0.1 - d * (0.08 + d * 0.02)));
        c.setDueAt(now.plus(Duration.ofDays(c.getInterval())));
        c.setLastReviewedAt(now);
    }
}
