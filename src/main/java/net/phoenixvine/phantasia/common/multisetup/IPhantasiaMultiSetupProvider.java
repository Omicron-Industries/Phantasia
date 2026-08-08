package net.phoenixvine.phantasia.common.multisetup;

import java.util.List;
import java.util.Optional;

public interface IPhantasiaMultiSetupProvider {

    String getModId();

    boolean isAvailable();

    List<IPhantasiaMultiSetup> getAllSetups();

    Optional<IPhantasiaMultiSetup> resolve(String id);
}
