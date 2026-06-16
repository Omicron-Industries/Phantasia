package net.phoenixvine.phantasia.common.data.guides;

/**
 * Registers guides that are shipped with Phantasia itself.
 * These are always present regardless of what a pack author provides,
 * and survive guide reloads.
 */
public final class PhantasiaBuiltinGuides {

    private PhantasiaBuiltinGuides() {}

    public static void register() {
        registerGettingStarted();
    }

    private static void registerGettingStarted() {
        PhantasiaGuideData guide = new PhantasiaGuideData("phantasia:getting_started", "Getting Started");
        guide.iconItem = "minecraft:knowledge_book";
        guide.subtitle = "New to Phantasia?";
        guide.tag = "intro";

        PhantasiaGuideData.PageData p1 = new PhantasiaGuideData.PageData();
        p1.headline = "What is Phantasia?";
        p1.text = "Phantasia is an interactive guide system built into this modpack.\n\n" +
                "It provides step-by-step assembly walkthroughs, 3D machine previews, " +
                "and reference pages for complex multiblock machinery — all accessible " +
                "without leaving the game.";
        guide.pages.add(p1);

        PhantasiaGuideData.PageData p2 = new PhantasiaGuideData.PageData();
        p2.headline = "Opening a Guide";
        p2.text = "Hold §b[P]§r while looking at a multiblock machine in the world. " +
                "A progress bar will appear at the bottom of the screen — keep " +
                "holding until it fills and the guide opens automatically.\n\n" +
                "Some items also have guides attached. Holding §b[P]§r while the item " +
                "is in your hand works the same way.";
        guide.pages.add(p2);

        PhantasiaGuideData.PageData p3 = new PhantasiaGuideData.PageData();
        p3.headline = "Browsing All Content";
        p3.text = "Type §b/phantasia§r to open a searchable list of every guide, " +
                "machine walkthrough, and multi-machine scene layout available in " +
                "this pack.\n\n" +
                "This is the quickest way to find documentation when you are not " +
                "standing near a machine.";
        guide.pages.add(p3);

        PhantasiaGuideData.PageData p4 = new PhantasiaGuideData.PageData();
        p4.headline = "Variants";
        p4.text = "Many machine previews support §bVariants§r — you can toggle optional " +
                "blocks such as fusion glass, hatch tiers, or casing alternatives " +
                "directly inside the viewer without rebuilding anything.\n\n" +
                "Your choices are remembered per machine, so each one keeps the view " +
                "you prefer across world restarts.";
        guide.pages.add(p4);

        PhantasiaGuideRegistry.registerBuiltin(guide);
    }
}
