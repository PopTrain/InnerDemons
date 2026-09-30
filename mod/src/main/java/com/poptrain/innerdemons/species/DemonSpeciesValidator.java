package com.poptrain.innerdemons.species;

import java.util.Map;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.poptrain.innerdemons.species.evolution.Evolution;
import com.poptrain.innerdemons.type.DemonType;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

public final class DemonSpeciesValidator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private DemonSpeciesValidator() {
    }

    static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            return;
        }
        event.getRegistryAccess().registry(DemonRegistries.SPECIES).ifPresent(species -> {
            validate(species);
            event.getRegistryAccess().registry(DemonRegistries.TYPE).ifPresent(types -> validateTypes(species, types));
        });
    }

    public static int validate(Registry<DemonSpecies> registry) {
        int problems = 0;
        for (Map.Entry<ResourceKey<DemonSpecies>, DemonSpecies> entry : registry.entrySet()) {
            ResourceKey<DemonSpecies> key = entry.getKey();
            for (Evolution evolution : entry.getValue().evolutions()) {
                if (evolution.target().equals(key)) {
                    LOGGER.warn("Demon species {} evolves into itself", key.location());
                    problems++;
                } else if (!registry.containsKey(evolution.target())) {
                    LOGGER.warn("Demon species {} evolves into unknown species {}", key.location(), evolution.target().location());
                    problems++;
                }
            }
        }
        LOGGER.info("Loaded {} demon species with {} problem(s)", registry.size(), problems);
        return problems;
    }

    public static int validateTypes(Registry<DemonSpecies> species, Registry<DemonType> types) {
        int problems = 0;
        for (Map.Entry<ResourceKey<DemonSpecies>, DemonSpecies> entry : species.entrySet()) {
            for (ResourceKey<DemonType> type : entry.getValue().types()) {
                if (!types.containsKey(type)) {
                    LOGGER.warn("Demon species {} has unknown type {}", entry.getKey().location(), type.location());
                    problems++;
                }
            }
        }
        return problems;
    }
}
