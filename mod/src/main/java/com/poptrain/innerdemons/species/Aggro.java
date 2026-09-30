package com.poptrain.innerdemons.species;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

public enum Aggro implements StringRepresentable {
  FRIENDLY,
  PASSIVE,
  NEUTRAL,
  TERRITORIAL,
  HOSTILE;

  public static final Codec<Aggro> CODEC =
      StringRepresentable.fromEnum(Aggro::values);

  @Override
  public String getSerializedName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
