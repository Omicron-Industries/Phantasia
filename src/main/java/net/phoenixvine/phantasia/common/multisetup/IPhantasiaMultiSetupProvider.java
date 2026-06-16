package net.phoenixvine.phantasia.common.multisetup;

import java.util.List;
import java.util.Optional;

/**
 * Registered by each mod integration to supply multi-component setup definitions.
 * Parallel to {@link net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockProvider}
 * but recipe-centric rather than structure-centric.
 */
public interface IPhantasiaMultiSetupProvider {

    String getModId();

    boolean isAvailable();

    List<IPhantasiaMultiSetup> getAllSetups();

    Optional<IPhantasiaMultiSetup> resolve(String id);
}
