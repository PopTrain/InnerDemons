package com.poptrain.innerdemons.core.schedule;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class TickSchedulerNbt {

    private static final String CLOCK = "Clock";
    private static final String TASKS = "Tasks";
    private static final String KEY = "Key";
    private static final String NAME = "Name";
    private static final String REMAINING = "Remaining";
    private static final String TIMEOUT = "Timeout";
    private static final String PERIOD_MIN = "PeriodMin";
    private static final String PERIOD_MAX = "PeriodMax";
    private static final String RUNS_LEFT = "RunsLeft";
    private static final String RUNS = "Runs";
    private static final String PRIORITY = "Priority";

    private TickSchedulerNbt() {
    }

    public static CompoundTag save(TickScheduler<?> scheduler) {
        return write(scheduler.snapshot());
    }

    public static boolean load(TickScheduler<?> scheduler, CompoundTag tag) {
        if (!tag.contains(CLOCK, Tag.TAG_LONG)) {
            return false;
        }
        return scheduler.restore(read(tag));
    }

    public static CompoundTag write(SchedulerSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(CLOCK, snapshot.clock());
        ListTag tasks = new ListTag();
        for (TaskSnapshot task : snapshot.tasks()) {
            CompoundTag t = new CompoundTag();
            t.putString(KEY, task.key());
            t.putString(NAME, task.name());
            t.putInt(REMAINING, task.remaining());
            t.putInt(TIMEOUT, task.timeout());
            t.putInt(PERIOD_MIN, task.periodMin());
            t.putInt(PERIOD_MAX, task.periodMax());
            t.putInt(RUNS_LEFT, task.runsLeft());
            t.putInt(RUNS, task.runCount());
            t.putString(PRIORITY, task.priority());
            tasks.add(t);
        }
        tag.put(TASKS, tasks);
        return tag;
    }

    public static SchedulerSnapshot read(CompoundTag tag) {
        List<TaskSnapshot> tasks = new ArrayList<>();
        ListTag list = tag.getList(TASKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            String key = t.getString(KEY);
            if (key.isEmpty()) {
                continue;
            }
            String name = t.contains(NAME, Tag.TAG_STRING) ? t.getString(NAME) : key;
            tasks.add(new TaskSnapshot(key, name, t.getInt(REMAINING), t.getInt(TIMEOUT), t.getInt(PERIOD_MIN),
                    t.getInt(PERIOD_MAX), t.getInt(RUNS_LEFT), t.getInt(RUNS), t.getString(PRIORITY)));
        }
        return new SchedulerSnapshot(tag.getLong(CLOCK), tasks);
    }
}
