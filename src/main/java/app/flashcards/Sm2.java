package app.flashcards;

import java.time.Duration;
import java.time.Instant;

/** SM-2 ease updates with grade-specific intervals. Again=1, Hard=3, Good=4, Easy=5. */
final class Sm2 {
    private Sm2() {}

    // Keep frontend/src/logic.ts nextInterval in sync with these rules.
    private static int nextInterval(Card c, int quality) {
        if (c.getRepetitions() == 0) {
            return switch (quality) {
                case 3 -> 1;
                case 4 -> 2;
                default -> 4;
            };
        }
        int hard = Math.max(c.getInterval() + 1, (int) Math.round(c.getInterval() * 1.2));
        int good = Math.max(hard + 1, (int) Math.round(c.getInterval() * c.getEaseFactor()));
        int easy = Math.max(good + 1, (int) Math.round(c.getInterval() * c.getEaseFactor() * 1.3));
        return switch (quality) {
            case 3 -> hard;
            case 4 -> good;
            default -> easy;
        };
    }

    static void review(Card c, int quality, Instant now) {
        if (quality < 0 || quality > 5) throw new IllegalArgumentException("quality must be 0-5");
        if (quality >= 3) {
            c.setInterval(nextInterval(c, quality));
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
