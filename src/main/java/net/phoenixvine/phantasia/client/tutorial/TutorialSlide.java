package net.phoenixvine.phantasia.client.tutorial;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

public final class TutorialSlide {

    // ── Nested types ──────────────────────────────────────────────────────────

    @FunctionalInterface
    public interface MockRenderer {

        /** Draws the mock UI illustration inside the given rectangle. */
        void render(GuiGraphics g, int mx, int my, int mw, int mh, int tick);
    }

    /** A position (relative 0-1 within the mock area) the cursor visits. */
    public record CursorWaypoint(
                                 float relX, float relY,
                                 int travelTicks,   // ticks to lerp from previous waypoint
                                 int dwellTicks,    // ticks to hold here before moving on
                                 boolean click      // play click animation while dwelling
    ) {}

    /** A rectangle (relative 0-1) to spotlight/highlight on the mock. */
    public record Highlight(float relX, float relY, float relW, float relH, @Nullable String label) {

        public Highlight(float rx, float ry, float rw, float rh) {
            this(rx, ry, rw, rh, null);
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    public final String title;
    public final String text;
    @Nullable
    public final MockRenderer mock;
    public final List<Highlight> highlights;
    public final List<CursorWaypoint> cursor;

    private TutorialSlide(Builder b) {
        this.title = b.title;
        this.text = b.text;
        this.mock = b.mock;
        this.highlights = List.copyOf(b.highlights);
        this.cursor = List.copyOf(b.cursor);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder of(String title, String text) {
        return new Builder(title, text);
    }

    public static final class Builder {

        private final String title, text;
        private MockRenderer mock;
        private final List<Highlight> highlights = new ArrayList<>();
        private final List<CursorWaypoint> cursor = new ArrayList<>();

        private Builder(String title, String text) {
            this.title = title;
            this.text = text;
        }

        public Builder mock(MockRenderer r) {
            this.mock = r;
            return this;
        }

        public Builder highlight(float rx, float ry, float rw, float rh) {
            return highlight(rx, ry, rw, rh, null);
        }

        public Builder highlight(float rx, float ry, float rw, float rh, String label) {
            highlights.add(new Highlight(rx, ry, rw, rh, label));
            return this;
        }

        /** Move cursor to (rx, ry), arrive after travelTicks, stay for dwellTicks. */
        public Builder cursor(float rx, float ry, int travel, int dwell, boolean click) {
            cursor.add(new CursorWaypoint(rx, ry, travel, dwell, click));
            return this;
        }

        public Builder cursor(float rx, float ry, int travel, int dwell) {
            return cursor(rx, ry, travel, dwell, false);
        }

        public TutorialSlide build() {
            return new TutorialSlide(this);
        }
    }
}
