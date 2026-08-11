package io.github.gibatron.bowbattle.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;

public record BowBattleMapConfig(Identifier id) {
    public static final Codec<BowBattleMapConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(BowBattleMapConfig::id)
    ).apply(instance, BowBattleMapConfig::new));
}
