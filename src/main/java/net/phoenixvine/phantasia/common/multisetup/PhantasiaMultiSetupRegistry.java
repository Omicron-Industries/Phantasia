package net.phoenixvine.phantasia.common.multisetup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class PhantasiaMultiSetupRegistry {

    private static final List<IPhantasiaMultiSetupProvider> PROVIDERS = new ArrayList<>();

    private PhantasiaMultiSetupRegistry() {}

    public static void register(IPhantasiaMultiSetupProvider provider) {
        PROVIDERS.add(provider);
    }

    public static List<IPhantasiaMultiSetupProvider> getProviders() {
        return Collections.unmodifiableList(PROVIDERS);
    }

    public static List<IPhantasiaMultiSetup> getAllSetups() {
        List<IPhantasiaMultiSetup> all = new ArrayList<>();
        for (IPhantasiaMultiSetupProvider p : PROVIDERS) {
            if (p.isAvailable()) all.addAll(p.getAllSetups());
        }
        return all;
    }

    public static Optional<IPhantasiaMultiSetup> resolve(String id) {
        for (IPhantasiaMultiSetupProvider p : PROVIDERS) {
            if (!p.isAvailable()) continue;
            Optional<IPhantasiaMultiSetup> r = p.resolve(id);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }
}
