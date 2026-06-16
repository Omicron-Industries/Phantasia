package net.phoenixvine.phantasia.client.tutorial;

import java.util.List;

public final class TutorialSequence {

    public static final String PLAYER = "player";
    public static final String DEV    = "dev";

    public final String id;
    public final String title;
    public final String description;
    public final String iconItem;
    public final String category; // PLAYER or DEV
    public final List<TutorialSlide> slides;

    public TutorialSequence(String id, String title, String description,
                             String iconItem, String category, List<TutorialSlide> slides) {
        this.id          = id;
        this.title       = title;
        this.description = description;
        this.iconItem    = iconItem;
        this.category    = category;
        this.slides      = List.copyOf(slides);
    }
}
