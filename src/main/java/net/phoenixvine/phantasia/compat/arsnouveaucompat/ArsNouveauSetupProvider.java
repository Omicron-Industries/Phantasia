package net.phoenixvine.phantasia.compat.arsnouveaucompat;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.phantasia.common.multisetup.IPhantasiaMultiSetup;
import net.phoenixvine.phantasia.common.multisetup.IPhantasiaMultiSetupProvider;

import java.util.List;
import java.util.Optional;

public class ArsNouveauSetupProvider implements IPhantasiaMultiSetupProvider {

    private final List<ArsNouveauMultiSetup> setups;

    public ArsNouveauSetupProvider() {
        setups = List.of(
                new ArsNouveauMultiSetup(
                        ArsNouveauMultiSetup.SetupType.ENCHANTING_APPARATUS,
                        new ResourceLocation("ars_nouveau", "enchanting_apparatus"),
                        "Enchanting Apparatus"
                ),
                new ArsNouveauMultiSetup(
                        ArsNouveauMultiSetup.SetupType.IMBUEMENT_CHAMBER,
                        new ResourceLocation("ars_nouveau", "imbuement_chamber"),
                        "Imbuement Chamber"
                )
        );
    }

    @Override
    public String getModId() { return "ars_nouveau"; }

    @Override
    public boolean isAvailable() { return true; }

    @Override
    public List<IPhantasiaMultiSetup> getAllSetups() {
        return List.copyOf(setups);
    }

    @Override
    public Optional<IPhantasiaMultiSetup> resolve(String id) {
        return setups.stream()
                .filter(s -> s.getId().toString().equals(id))
                .map(s -> (IPhantasiaMultiSetup) s)
                .findFirst();
    }

    /** Called on resource reload to flush all recipe caches. */
    public void invalidateAll() {
        setups.forEach(ArsNouveauMultiSetup::invalidateCache);
    }
}
