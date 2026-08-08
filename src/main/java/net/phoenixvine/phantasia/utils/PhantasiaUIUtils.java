package net.phoenixvine.phantasia.utils;

public class PhantasiaUIUtils {

    public record ButtonAction(int x, int y, int w, int h, Runnable action) {

        public boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public static String truncate(net.minecraft.client.gui.Font font, String s, int maxPx) {
        if (s == null) return "";
        String result = s;
        while (font.width(result) > maxPx && result.length() > 2) {
            result = result.substring(0, result.length() - 2) + "\u2026";
        }
        return result;
    }
}
