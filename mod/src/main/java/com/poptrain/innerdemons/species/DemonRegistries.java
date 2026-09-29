package com.poptrain.innerdemons.species;

import java.util.Optional;

import com.poptrain.innerdemons.species.evolution.EvolutionMethod;
import com.poptrain.innerdemons.species.evolution.EvolutionMethods;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

public final class DemonRegistries {

    public static final ResourceKey<Registry<DemonSpecies>> SPECIES =
            ResourceKey.createRegistryKey(DemonCodecs.id("demon"));

    public static final ResourceKey<Registry<EvolutionMethod<?>>> EVOLUTION_METHOD =
            ResourceKey.createRegistryKey(DemonCodecs.id("evolution_method"));

    private DemonRegistries() {
    }

    public static void register(IEventBus modBus) {
        EvolutionMethods.METHODS.register(modBus);
        modBus.addListener(DemonRegistries::registerDataPackRegistries);
        NeoForge.EVENT_BUS.addListener(DemonSpeciesValidator::onTagsUpdated);
    }

    public static Registry<DemonSpecies> species(RegistryAccess access) {
        return access.registryOrThrow(SPECIES);
    }

    public static Optional<DemonSpecies> species(RegistryAccess access, ResourceLocation id) {
        return species(access).getOptional(id);
    }

    private static void registerDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(SPECIES, DemonSpecies.CODEC, DemonSpecies.CODEC);
    }
}
