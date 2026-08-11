package io.github.gibatron.bowbattle.game;

import io.github.gibatron.bowbattle.game.map.BowBattleMap;
import java.util.Set;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.api.util.ItemStackBuilder;

public record BowBattleSpawnLogic(ServerWorld world, BowBattleMap map) {
    public void resetPlayer(ServerPlayerEntity player, GameMode gameMode) {
        player.changeGameMode(gameMode);
        player.setVelocity(Vec3d.ZERO);
        player.clearStatusEffects();

        player.addStatusEffect(
            new StatusEffectInstance(
                StatusEffects.INVISIBILITY,
                20 * 2,
                1,
                true,
                false
            )
        );

        player.addStatusEffect(
            new StatusEffectInstance(
                StatusEffects.SPEED,
                StatusEffectInstance.INFINITE,
                1,
                true,
                false
            )
        );

        player.getInventory().clear();
        ItemStack bow = ItemStackBuilder.of(Items.BOW).setUnbreakable().build();
        player.getInventory().setStack(0, bow);
        player.getInventory().insertStack(17, new ItemStack(Items.ARROW, 1));
        player.setExperiencePoints(0);
        player.setExperienceLevel(1);
    }

    public void resetWaitingPlayer(
        ServerPlayerEntity player,
        GameMode gameMode
    ) {
        player.changeGameMode(gameMode);
        player.getInventory().clear();
        player.clearStatusEffects();
    }

    public void spawnPlayer(ServerPlayerEntity player) {
        ServerWorld world = this.world;

        Vec3d pos =
            this.map.getSpawn(
                    player.getRandom().nextInt(this.map.spawns.size())
                ).centerBottom();
        player.teleport(
            world,
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            Set.of(),
            0.0F,
            0.0F,
            false
        );
    }
}
