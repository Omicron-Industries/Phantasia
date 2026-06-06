package net.phoenixvine.phantasia.common;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.phoenixvine.phantasia.client.camera.LerpType;
import net.phoenixvine.phantasia.client.screens.PhantasiaSceneScreen;
import net.phoenixvine.phantasia.common.PhantasiaParticleEffect;

import lombok.Getter;

import java.util.*;
import java.util.function.Predicate;

import javax.annotation.Nullable;

@Getter
public class PhantasiaScript {

    public record Step(
            int tickOffset,
            String caption,
            Predicate<BlockPos> filter,
            boolean working,
            int forceShape,
            int forceCoil,
            float yaw,
            float pitch,
            float zoom,
            boolean useCam,
            LerpType lerpType,
            int lerpTicks,
            @Nullable String fakeRecipeId,
            List<PhantasiaParticleEffect> particleEffects) {

        public boolean hasCamera() {
            return useCam;
        }

        @Nullable
        public String fakeRecipeId() {
            return this.fakeRecipeId;
        }
    }

    public record LocalWarning(BlockPos localPos, String label, int color) {

        public LocalWarning(BlockPos localPos, String label) {
            this(localPos, label, 0xFFFFB74D);
        }
    }

    public record HeatmapTier(String name, int color, Predicate<BlockPos> matcher) {}

    private final PhantasiaScriptData sourceData;

    private final List<Step> steps;
    private final int totalTicks;
    private final List<LocalWarning> commonMistakes;
    private final List<String> globalMistakes;
    private final List<HeatmapTier> heatmapTiers;
    private final List<PhantasiaVariantGroup> variantGroups;

    private PhantasiaScript(PhantasiaScriptData data,
                            List<Step> steps,
                            List<LocalWarning> commonMistakes,
                            List<String> globalMistakes,
                            List<HeatmapTier> heatmapTiers,
                            List<PhantasiaVariantGroup> variantGroups) {
        this.sourceData = data;
        this.steps = Collections.unmodifiableList(steps);
        this.commonMistakes = Collections.unmodifiableList(commonMistakes);
        this.globalMistakes = Collections.unmodifiableList(globalMistakes);
        this.heatmapTiers = Collections.unmodifiableList(heatmapTiers);
        this.variantGroups = Collections.unmodifiableList(variantGroups);
        this.totalTicks = steps.isEmpty() ? 60 : steps.get(steps.size() - 1).tickOffset() + 60;
    }

    public boolean hasMistakes() {
        return !commonMistakes.isEmpty() || !globalMistakes.isEmpty();
    }

    public boolean hasCommonMistakes() {
        return !commonMistakes.isEmpty();
    }

    public boolean hasHeatmap() {
        return !heatmapTiers.isEmpty();
    }

    @Nullable
    public PhantasiaScriptData.StartCameraData getStartCamera() {
        return sourceData != null ? sourceData.getStartCamera() : null;
    }

    public Step getActiveStep(int currentTick) {
        Step active = null;
        for (Step s : steps) {
            if (s.tickOffset() <= currentTick) active = s;
            else break;
        }
        return active;
    }

    public int getActiveStepIndex(int currentTick) {
        int idx = 0;
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).tickOffset() <= currentTick) idx = i;
            else break;
        }
        return idx;
    }

    public static PhantasiaScript fromData(PhantasiaScriptData data) {
        List<Step> steps = new ArrayList<>();
        for (PhantasiaScriptData.StepData sd : data.getSteps())
            steps.add(compileStep(sd));

        List<LocalWarning> mistakes = new ArrayList<>();
        for (PhantasiaScriptData.MistakeData md : data.getMistakes())
            mistakes.add(new LocalWarning(new BlockPos(md.x, md.y, md.z), md.label, md.colorArgb()));

        List<String> globalMistakes = new ArrayList<>(data.getGlobalMistakes());
        List<HeatmapTier> tiers = new ArrayList<>();
        // Variant groups require a loaded pattern — compiled later via withVariants().
        return new PhantasiaScript(data, steps, mistakes, globalMistakes, tiers,
                Collections.emptyList());
    }

    /**
     * Compiles variant groups for this script given the loaded pattern and active
     * shape info. Returns a new {@link PhantasiaScript} with the groups attached.
     *
     * <p>Called by {@link PhantasiaSceneScreen} after the pattern has been loaded,
     * because block world-positions are needed to build the position→group maps.
     */
    /**
     * Compiles variant groups for this script given the loaded pattern and ALL
     * available shapes for the machine. Passing all shapes is required so that
     * auto-detection can find blocks that only appear in some shape variants
     * (e.g. fusion glass only appears in the fusion reactor's shape index 1,
     * not in the all-casing shape index 0).
     *
     * Returns a new {@link PhantasiaScript} with the compiled groups attached.
     * Called by {@link net.phoenixvine.phantasia.client.screens.PhantasiaSceneScreen}
     * after the pattern has been loaded.
     */
    public PhantasiaScript withVariants(MultiblockMachineDefinition definition,
                                        PhantasiaLoadedPattern pattern,
                                        java.util.List<MultiblockShapeInfo> allShapes) {
        List<PhantasiaVariantGroup> groups =
                PhantasiaVariantGroup.compile(sourceData, definition, pattern, allShapes);
        return new PhantasiaScript(sourceData, steps, commonMistakes, globalMistakes,
                heatmapTiers, groups);
    }

    private static Step compileStep(PhantasiaScriptData.StepData sd) {
        return compileStepWithParticles(sd, List.of());
    }

    private static Step compileStepWithParticles(PhantasiaScriptData.StepData sd,
                                                 List<PhantasiaParticleEffect> particleFx) {
        Predicate<BlockPos> allow = buildShowPredicate(sd);
        Predicate<BlockPos> deny = buildHidePredicate(sd);
        Predicate<BlockPos> filter = pos -> allow.test(pos) && !deny.test(pos);

        float yaw = 0f;
        float pitch = 0f;
        float zoom = -1f;
        boolean useCam = false;
        LerpType lerpType = LerpType.SNAP;
        int lerpTicks = 0;

        if (sd.camera != null) {
            yaw = sd.camera.yaw;
            pitch = sd.camera.pitch;
            zoom = sd.camera.zoom;
            useCam = true;
            lerpType = LerpType.fromString(sd.camera.lerpType);
            lerpTicks = sd.camera.lerpTicks;
        }

        return new Step(sd.tick, sd.caption, filter,
                sd.working, -1, -1,
                yaw, pitch, zoom, useCam, lerpType, lerpTicks,
                sd.fakeRecipeId, Collections.unmodifiableList(new ArrayList<>(particleFx)));
    }

    private static Predicate<BlockPos> buildShowPredicate(PhantasiaScriptData.StepData sd) {
        String show = sd.show == null ? "all" : sd.show.toLowerCase(Locale.ROOT);
        return switch (show) {
            case "all" -> pos -> true;

            case "layer" -> {
                int y = sd.layer;
                yield pos -> pos.getY() == y;
            }

            case "layers" -> {
                int lo = sd.layerMin, hi = sd.layerMax;
                yield pos -> pos.getY() >= lo && pos.getY() <= hi;
            }

            case "pos" -> {
                Set<BlockPos> set = new HashSet<>();
                for (int[] xyz : sd.positions)
                    if (xyz.length >= 3) set.add(new BlockPos(xyz[0], xyz[1], xyz[2]));
                yield set::contains;
            }

            case "parts" -> localPred(state -> {
                if (!(state.getBlock() instanceof MetaMachineBlock mmb)) return false;
                if (mmb.getDefinition() instanceof MultiblockMachineDefinition) return false;
                String p = mmb.getDefinition().getId().getPath();
                return p.contains("hatch") || p.contains("bus") || p.contains("port") || p.contains("storage") ||
                        p.contains("input") || p.contains("output") || p.contains("muffler") ||
                        p.contains("maintenance");
            });

            case "controller" -> localPred(state -> state.getBlock() instanceof MetaMachineBlock mmb &&
                    mmb.getDefinition() instanceof MultiblockMachineDefinition);

            case "functional" -> localPred(state -> {
                if (state.isAir()) return false;
                return state.getBlock() instanceof MetaMachineBlock ||
                        state.getBlock().getDescriptionId().contains("frame") ||
                        state.getBlock().getDescriptionId().contains("gearbox");
            });

            default -> {
                // parts:keyword — single keyword match, e.g. "parts:hatch", "parts:muffler"
                if (show.startsWith("parts:")) {
                    String keyword = show.substring(6);
                    yield localPred(state -> {
                        if (!(state.getBlock() instanceof MetaMachineBlock mmb)) return false;
                        if (mmb.getDefinition() instanceof MultiblockMachineDefinition) return false;
                        return mmb.getDefinition().getId().getPath().contains(keyword);
                    });
                }
                yield pos -> true;
            }
        };
    }

    private static Predicate<BlockPos> buildHidePredicate(PhantasiaScriptData.StepData sd) {
        Predicate<BlockPos> deny = pos -> false;

        if (sd.hideLayer >= 0) {
            int hy = sd.hideLayer;
            deny = deny.or(pos -> pos.getY() == hy);
        }

        if (!sd.hidePositions.isEmpty()) {
            Set<BlockPos> hidden = new HashSet<>();
            for (int[] xyz : sd.hidePositions)
                if (xyz.length >= 3) hidden.add(new BlockPos(xyz[0], xyz[1], xyz[2]));
            deny = deny.or(hidden::contains);
        }

        return deny;
    }

    private static Predicate<BlockPos> localPred(Predicate<BlockState> statePred) {
        return localPos -> {
            if (PhantasiaSceneScreen.SHARED_LEVEL == null) return false;
            BlockPos wp = localPos.offset(PhantasiaSceneScreen.getOriginForCurrentPattern());
            try {
                return statePred.test(PhantasiaSceneScreen.SHARED_LEVEL.getBlockState(wp));
            } catch (Exception e) {
                return false;
            }
        };
    }

    public static PhantasiaScript showAll() {
        return fromData(PhantasiaScriptData.defaultFor(""));
    }

    public static PhantasiaScript simple(String caption) {
        return fromData(PhantasiaScriptData.simpleFor("", caption));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final PhantasiaScriptData data = new PhantasiaScriptData();
        private PhantasiaScriptData.StepData pending = null;

        // Particle effects are client-side only and not part of PhantasiaScriptData.
        // We accumulate them keyed by step index and attach after compileStep().
        private final List<List<PhantasiaParticleEffect>> pendingParticles = new ArrayList<>();
        private List<PhantasiaParticleEffect> currentParticles = new ArrayList<>();

        public Builder step(int tick, String caption) {
            commit();
            pending = new PhantasiaScriptData.StepData(tick, caption);
            return this;
        }

        public Builder showAll() {
            step().show = "all";
            return this;
        }

        public Builder showLayer(int y) {
            step().show = "layer";
            step().layer = y;
            return this;
        }

        public Builder showLayers(int lo, int hi) {
            step().show = "layers";
            step().layerMin = lo;
            step().layerMax = hi;
            return this;
        }

        public Builder showParts() {
            step().show = "parts";
            return this;
        }

        public Builder showController() {
            step().show = "controller";
            return this;
        }

        public Builder showFunctional() {
            step().show = "functional";
            return this;
        }

        public Builder showPos(BlockPos... positions) {
            step().show = "pos";
            for (BlockPos p : positions) step().positions.add(new int[] { p.getX(), p.getY(), p.getZ() });
            return this;
        }

        public Builder hideLayer(int y) {
            step().hideLayer = y;
            return this;
        }

        public Builder hidePos(BlockPos... ps) {
            for (BlockPos p : ps) step().hidePositions.add(new int[] { p.getX(), p.getY(), p.getZ() });
            return this;
        }

        public Builder working(boolean w) {
            step().working = w;
            return this;
        }

        public Builder fakeRecipe(String recipeId) {
            step().fakeRecipeId = recipeId;
            return this;
        }

        public Builder camera(float yaw, float pitch) {
            step().camera = new PhantasiaScriptData.CameraData(yaw, pitch);
            return this;
        }

        public Builder camera(float yaw, float pitch, float zoom) {
            step().camera = new PhantasiaScriptData.CameraData(yaw, pitch, zoom);
            return this;
        }

        public Builder camera(float yaw, float pitch, float zoom, LerpType lerpType, int lerpTicks) {
            step().camera = new PhantasiaScriptData.CameraData(yaw, pitch, zoom,
                    lerpType.name(), lerpTicks);
            return this;
        }

        // ── Particle effects ──────────────────────────────────────────────────

        /**
         * Adds a named preset particle effect at a local block position.
         * Presets: HIGHLIGHT, ATTENTION, WARNING, SUCCESS, SMOKE, SPARK.
         */
        public Builder particle(int x, int y, int z, PhantasiaParticleEffect.Preset preset) {
            currentParticles.add(PhantasiaParticleEffect.preset(new BlockPos(x, y, z), preset));
            return this;
        }

        public Builder particle(int x, int y, int z, PhantasiaParticleEffect.Preset preset,
                                int intervalTicks) {
            currentParticles.add(
                    PhantasiaParticleEffect.preset(new BlockPos(x, y, z), preset, intervalTicks));
            return this;
        }

        public Builder particle(int x, int y, int z, String particleId,
                                int count, float spread, float speed) {
            currentParticles.add(
                    PhantasiaParticleEffect.vanilla(new BlockPos(x, y, z), particleId,
                            count, spread, speed));
            return this;
        }

        public Builder particle(int x, int y, int z, String particleId,
                                int count, float spread, float speed, int intervalTicks) {
            currentParticles.add(
                    PhantasiaParticleEffect.vanilla(new BlockPos(x, y, z), particleId,
                            count, spread, speed, intervalTicks));
            return this;
        }

        public Builder particleAmbient(int x, int y, int z) {
            currentParticles.add(PhantasiaParticleEffect.ambient(new BlockPos(x, y, z)));
            return this;
        }

        public Builder particleAmbient(int x, int y, int z, int intervalTicks) {
            currentParticles.add(
                    PhantasiaParticleEffect.ambient(new BlockPos(x, y, z), intervalTicks));
            return this;
        }

        public Builder mistake(int x, int y, int z, String label) {
            data.getMistakes().add(new PhantasiaScriptData.MistakeData(x, y, z, label));
            return this;
        }

        public Builder mistake(int x, int y, int z, String label, int argb) {
            data.getMistakes().add(new PhantasiaScriptData.MistakeData(x, y, z, label,
                    String.format("%06X", argb & 0xFFFFFF)));
            return this;
        }

        public Builder mistake(String global) {
            data.getGlobalMistakes().add(global);
            return this;
        }

        public PhantasiaScript build() {
            commit();
            // Compile steps and attach particle effects by step index.
            List<PhantasiaScript.Step> compiled = new ArrayList<>();
            int i = 0;
            for (PhantasiaScriptData.StepData sd : data.getSteps()) {
                List<PhantasiaParticleEffect> fx =
                        i < pendingParticles.size() ? pendingParticles.get(i) : List.of();
                compiled.add(compileStepWithParticles(sd, fx));
                i++;
            }
            List<LocalWarning> mistakes = new ArrayList<>();
            for (PhantasiaScriptData.MistakeData md : data.getMistakes())
                mistakes.add(new LocalWarning(new BlockPos(md.x, md.y, md.z), md.label, md.colorArgb()));
            List<String> globalMistakes = new ArrayList<>(data.getGlobalMistakes());
            // Variant groups require a pattern — not available from the Builder path.
            return new PhantasiaScript(data, compiled, mistakes, globalMistakes, List.of(),
                    List.of());
        }

        public PhantasiaScriptData buildData() {
            commit();
            return data;
        }

        private PhantasiaScriptData.StepData step() {
            if (pending == null) pending = new PhantasiaScriptData.StepData(0, null);
            return pending;
        }

        private void commit() {
            if (pending != null) {
                data.getSteps().add(pending);
                pendingParticles.add(new ArrayList<>(currentParticles));
                currentParticles = new ArrayList<>();
                pending = null;
            }
        }
    }
}