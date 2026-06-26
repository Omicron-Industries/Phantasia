package net.phoenixvine.phantasia.compat.gtceu;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;

import com.mojang.blaze3d.vertex.PoseStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;

/**
 * GTCEu-specific dynamic rendering logic, isolated into the compat package so that
 * {@code PhantasiaWorldRenderer} does not directly reference GTCEu classes at bytecode level.
 * Only loaded when GTCEu is present.
 */
public final class GTCEuDynamicRenderHelper {

    private static final Logger LOGGER = LogManager.getLogger("Phantasia/GTCEuDynRender");

    private GTCEuDynamicRenderHelper() {}

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void drawDynamicRenderers(PhantasiaTrackedDummyWorld world,
                                            Set<BlockPos> frontTileEntities,
                                            BlockPos slotOrigin,
                                            PoseStack poseStack,
                                            MultiBufferSource.BufferSource buffers,
                                            float partial,
                                            float camX, float camY, float camZ) {
        Vec3 cameraPos = new Vec3(camX, camY, camZ);
        for (BlockPos pos : frontTileEntities) {
            BlockEntity be = world.getBlockEntity(pos);
            if (!(be instanceof MetaMachineBlockEntity mmbe)) continue;
            MetaMachine machine = mmbe.getMetaMachine();
            if (machine == null) continue;

            BlockPos machinePos = machine.getPos();

            com.lowdragmc.lowdraglib.client.renderer.IRenderer iRenderer = null;
            try {
                com.gregtechceu.gtceu.api.machine.MachineDefinition def = machine.getDefinition();
                for (Class<?> defCls = def.getClass(); defCls != null &&
                        defCls != Object.class; defCls = defCls.getSuperclass()) {
                    for (java.lang.reflect.Field f : defCls.getDeclaredFields()) {
                        if (com.lowdragmc.lowdraglib.client.renderer.IRenderer.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            iRenderer = (com.lowdragmc.lowdraglib.client.renderer.IRenderer) f.get(def);
                            break;
                        }
                    }
                    if (iRenderer != null) break;
                }
            } catch (Exception ignored) {}
            if (iRenderer == null) continue;

            for (Class<?> cls = iRenderer.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
                for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(iRenderer);
                        if (val == null) continue;
                        if (val instanceof DynamicRender<?, ?> dr) {
                            renderOneDynamic(dr, machine, machinePos, slotOrigin, cameraPos, partial, poseStack,
                                    buffers);
                        } else if (val instanceof java.util.List<?> list) {
                            for (Object item : list) {
                                if (item instanceof DynamicRender<?, ?> dr) {
                                    renderOneDynamic(dr, machine, machinePos, slotOrigin, cameraPos, partial, poseStack,
                                            buffers);
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void renderOneDynamic(DynamicRender dr, MetaMachine machine, BlockPos machinePos,
                                         BlockPos slotOrigin, Vec3 cameraPos, float partial,
                                         PoseStack poseStack, MultiBufferSource.BufferSource buffers) {
        try {
            if (!dr.shouldRender(machine, cameraPos)) return;
        } catch (Throwable ignored) {}

        poseStack.pushPose();
        poseStack.translate(machinePos.getX() - slotOrigin.getX(),
                machinePos.getY() - slotOrigin.getY(),
                machinePos.getZ() - slotOrigin.getZ());

        try {
            dr.render(machine, partial, poseStack, buffers,
                    15728880, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        } catch (Throwable e) {
            LOGGER.warn("[Phantasia] DynamicRender error at {}: {}", machinePos, e.getMessage());
        }
        poseStack.popPose();
    }
}
