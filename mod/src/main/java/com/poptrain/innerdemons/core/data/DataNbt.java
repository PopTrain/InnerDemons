package com.poptrain.innerdemons.core.data;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DataNbt {

    private DataNbt() {
    }

    public static CompoundTag save(DataContainer container) {
        return write(container.snapshot());
    }

    public static boolean load(DataContainer container, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return false;
        }
        return container.restore(read(tag));
    }

    public static CompoundTag write(DataSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        snapshot.values().forEach((key, tree) -> tag.put(key, toTag(tree)));
        return tag;
    }

    public static DataSnapshot read(CompoundTag tag) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : tag.getAllKeys()) {
            values.put(key, fromTag(tag.get(key)));
        }
        return new DataSnapshot(values);
    }

    public static Tag toTag(Object tree) {
        if (tree instanceof Tag tag) {
            return tag.copy();
        }
        if (tree instanceof Boolean bool) {
            return ByteTag.valueOf(bool);
        }
        if (tree instanceof Byte b) {
            return ByteTag.valueOf(b);
        }
        if (tree instanceof Short s) {
            return ShortTag.valueOf(s);
        }
        if (tree instanceof Integer i) {
            return IntTag.valueOf(i);
        }
        if (tree instanceof Long l) {
            return LongTag.valueOf(l);
        }
        if (tree instanceof Float f) {
            return FloatTag.valueOf(f);
        }
        if (tree instanceof Double d) {
            return DoubleTag.valueOf(d);
        }
        if (tree instanceof String s) {
            return StringTag.valueOf(s);
        }
        if (tree instanceof int[] ints) {
            return new IntArrayTag(ints.clone());
        }
        if (tree instanceof long[] longs) {
            return new LongArrayTag(longs.clone());
        }
        if (tree instanceof byte[] bytes) {
            return new ByteArrayTag(bytes.clone());
        }
        if (tree instanceof Map<?, ?> map) {
            CompoundTag compound = new CompoundTag();
            map.forEach((k, v) -> {
                if (v != null) {
                    compound.put(String.valueOf(k), toTag(v));
                }
            });
            return compound;
        }
        if (tree instanceof List<?> list) {
            ListTag out = new ListTag();
            for (Object element : list) {
                Tag tag = toTag(element);
                if (!out.addTag(out.size(), tag)) {
                    throw new DataException("Lists must hold one kind of value; found " + tag.getType().getName()
                            + " in a list of " + out.getElementType());
                }
            }
            return out;
        }
        throw new DataException("Cannot store " + (tree == null ? "null" : tree.getClass().getName()) + " as NBT");
    }

    public static Object fromTag(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : compound.getAllKeys()) {
                map.put(key, fromTag(compound.get(key)));
            }
            return map;
        }
        if (tag instanceof ListTag list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Tag element : list) {
                out.add(fromTag(element));
            }
            return out;
        }
        if (tag instanceof ByteTag b) {
            return b.getAsByte();
        }
        if (tag instanceof ShortTag s) {
            return s.getAsShort();
        }
        if (tag instanceof IntTag i) {
            return i.getAsInt();
        }
        if (tag instanceof LongTag l) {
            return l.getAsLong();
        }
        if (tag instanceof FloatTag f) {
            return f.getAsFloat();
        }
        if (tag instanceof DoubleTag d) {
            return d.getAsDouble();
        }
        if (tag instanceof StringTag s) {
            return s.getAsString();
        }
        if (tag instanceof IntArrayTag ints) {
            return ints.getAsIntArray().clone();
        }
        if (tag instanceof LongArrayTag longs) {
            return longs.getAsLongArray().clone();
        }
        if (tag instanceof ByteArrayTag bytes) {
            return bytes.getAsByteArray().clone();
        }
        throw new DataException("Unsupported NBT tag " + (tag == null ? "null" : tag.getType().getName()));
    }
}
