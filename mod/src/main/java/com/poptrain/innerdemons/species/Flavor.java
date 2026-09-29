package com.poptrain.innerdemons.species;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

public enum Flavor implements StringRepresentable {
  NEUTRAL,
  SWEET,
  SPICY,
  BITTER,
  SOUR,
  SAVORY;

  public static final Codec<Flavor> CODEC =
      StringRepresentable.fromEnum(Flavor::values);

  @Override
  public String getSerializedName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
