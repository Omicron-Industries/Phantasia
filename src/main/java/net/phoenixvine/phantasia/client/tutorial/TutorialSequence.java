package net.phoenixvine.phantasia.client.tutorial;

import net.minecraft.network.chat.Component;

import java.util.List;

public final class TutorialSequence {

    public static final String PLAYER = "player";
    public static final String DEV = "dev";

    public final String id;
    public final Component title;
    public final Component description;
    public final String iconItem;
    public final String category; // PLAYER or DEV
    public final List<TutorialSlide> slides;

    public TutorialSequence(String id, Component title, Component description,
                            String iconItem, String category, List<TutorialSlide> slides) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.iconItem = iconItem;
        this.category = category;
        this.slides = List.copyOf(slides);
    }
}
