package net.phoenixvine.phantasia.utils;

import net.phoenixvine.phantasia.Phantasia;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class StructureAssetReader {

    public static String[][] readAislesFromAsset(String assetPath) {
        List<List<String>> allAisles = new ArrayList<>();
        List<String> currentAisle = new ArrayList<>();

        try (InputStream is = StructureAssetReader.class.getResourceAsStream(assetPath)) {
            if (is == null) {
                throw new IllegalArgumentException("Structure asset not found: " + assetPath);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (line.startsWith(".aisle(")) {
                        if (!currentAisle.isEmpty()) {
                            allAisles.add(currentAisle);
                            currentAisle = new ArrayList<>();
                        }

                        line = line.substring(7);
                    }

                    if (line.endsWith(");") || line.endsWith(")")) {
                        line = line.replaceAll("\\);|\\)$", "").trim();
                    }

                    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"([^\"]*)\"").matcher(line);
                    while (matcher.find()) {
                        String cleanRow = matcher.group(1);
                        if (!cleanRow.isEmpty()) {
                            currentAisle.add(cleanRow);
                        }
                    }
                }

                if (!currentAisle.isEmpty()) {
                    allAisles.add(currentAisle);
                }

            }
        } catch (Exception e) {
            Phantasia.LOGGER.error("[Phantasia/StructureAsset] Failed to read {}: {}", assetPath, e.getMessage());
        }

        String[][] outcome = new String[allAisles.size()][];
        for (int i = 0; i < allAisles.size(); i++) {
            outcome[i] = allAisles.get(i).toArray(new String[0]);
        }
        return outcome;
    }
}
