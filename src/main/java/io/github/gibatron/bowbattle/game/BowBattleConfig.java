package io.github.gibatron.bowbattle.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.gibatron.bowbattle.game.map.BowBattleMapConfig;
import xyz.nucleoid.plasmid.api.game.common.config.WaitingLobbyConfig;

public record BowBattleConfig(
    WaitingLobbyConfig players,
    BowBattleMapConfig map,
    int timeLimitSecs
) {
    public static final MapCodec<BowBattleConfig> CODEC =
        RecordCodecBuilder.mapCodec(instance ->
            instance
                .group(
                    WaitingLobbyConfig.CODEC.fieldOf("players").forGetter(
                        BowBattleConfig::players
                    ),
                    BowBattleMapConfig.CODEC.fieldOf("map").forGetter(
                        BowBattleConfig::map
                    ),
                    Codec.INT.fieldOf("time_limit_secs").forGetter(
                        BowBattleConfig::timeLimitSecs
                    )
                )
                .apply(instance, BowBattleConfig::new)
        );
}
