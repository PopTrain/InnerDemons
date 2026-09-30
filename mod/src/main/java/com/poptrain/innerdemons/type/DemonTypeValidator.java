package com.poptrain.innerdemons.type;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.poptrain.innerdemons.species.DemonRegistries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

public final class DemonTypeValidator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private DemonTypeValidator() {
    }

    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            return;
        }
        event.getRegistryAccess().registry(DemonRegistries.TYPE).ifPresent(DemonTypeValidator::validate);
    }

    public static int validate(Registry<DemonType> registry) {
        int problems = 0;
        for (Map.Entry<ResourceKey<DemonType>, DemonType> entry : registry.entrySet()) {
            ResourceKey<DemonType> key = entry.getKey();
            DemonType type = entry.getValue();
            problems += checkList(registry, key, "strengths", type.strengths());
            problems += checkList(registry, key, "weaknesses", type.weaknesses());
            problems += checkList(registry, key, "resistances", type.resistances());
            problems += checkList(registry, key, "immunities", type.immunities());
            problems += checkOverlap(key, "weaknesses", type.weaknesses(), "resistances", type.resistances());
            problems += checkOverlap(key, "weaknesses", type.weaknesses(), "immunities", type.immunities());
            problems += checkOverlap(key, "resistances", type.resistances(), "immunities", type.immunities());
        }
        LOGGER.info("Loaded {} demon types with {} problem(s)", registry.size(), problems);
        return problems;
    }

    private static int checkList(Registry<DemonType> registry, ResourceKey<DemonType> owner, String field,
            List<ResourceKey<DemonType>> entries) {
        int problems = 0;
        Set<ResourceKey<DemonType>> seen = new HashSet<>();
        for (ResourceKey<DemonType> entry : entries) {
            if (!seen.add(entry)) {
                LOGGER.warn("Demon type {} lists {} more than once in {}", owner.location(), entry.location(), field);
                problems++;
            }
            if (!registry.containsKey(entry)) {
                LOGGER.warn("Demon type {} lists unknown type {} in {}", owner.location(), entry.location(), field);
                problems++;
            }
        }
        return problems;
    }

    private static int checkOverlap(ResourceKey<DemonType> owner, String firstField, List<ResourceKey<DemonType>> first,
            String secondField, List<ResourceKey<DemonType>> second) {
        int problems = 0;
        for (ResourceKey<DemonType> entry : first) {
            if (second.contains(entry)) {
                LOGGER.warn("Demon type {} lists {} in both {} and {}", owner.location(), entry.location(), firstField, secondField);
                problems++;
            }
        }
        return problems;
    }
}
