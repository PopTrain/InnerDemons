package com.poptrain.innerdemons.core.statemachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public final class StateMachineNbt {

    private static final String PATH = "Path";
    private static final String TICKS = "Ticks";
    private static final String HISTORY = "History";

    private StateMachineNbt() {
    }

    public static CompoundTag save(StateMachine<?, ?> machine) {
        return write(machine.snapshot());
    }

    public static boolean load(StateMachine<?, ?> machine, CompoundTag tag) {
        return machine.restore(read(tag));
    }

    public static CompoundTag write(StateSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        ListTag path = new ListTag();
        for (String state : snapshot.path()) {
            path.add(StringTag.valueOf(state));
        }
        tag.put(PATH, path);
        tag.putIntArray(TICKS, snapshot.ticks().stream().mapToInt(Integer::intValue).toArray());
        CompoundTag history = new CompoundTag();
        snapshot.history().forEach(history::putString);
        tag.put(HISTORY, history);
        return tag;
    }

    public static StateSnapshot read(CompoundTag tag) {
        List<String> path = new ArrayList<>();
        ListTag list = tag.getList(PATH, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            path.add(list.getString(i));
        }
        List<Integer> ticks = new ArrayList<>();
        for (int value : tag.getIntArray(TICKS)) {
            ticks.add(value);
        }
        Map<String, String> history = new HashMap<>();
        CompoundTag historyTag = tag.getCompound(HISTORY);
        for (String key : historyTag.getAllKeys()) {
            history.put(key, historyTag.getString(key));
        }
        return new StateSnapshot(path, ticks, history);
    }
}
