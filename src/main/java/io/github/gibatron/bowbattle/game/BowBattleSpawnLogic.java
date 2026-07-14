package io.github.gibatron.bowbattle.game;

import io.github.gibatron.bowbattle.game.map.BowBattleMap;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import xyz.nucleoid.plasmid.api.util.ItemStackBuilder;

import java.util.Set;

public record BowBattleSpawnLogic(ServerLevel world, BowBattleMap map) {

    public void resetPlayer(ServerPlayer player, GameType gameMode) {
        player.setGameMode(gameMode);
        player.setDeltaMovement(Vec3.ZERO);
        player.removeAllEffects();

        player.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                20 * 2,
                1,
                true,
                false
        ));

        player.addEffect(new MobEffectInstance(
                MobEffects.SPEED,
                MobEffectInstance.INFINITE_DURATION,
                1,
                true,
                false
        ));

        player.getInventory().clearContent();
        ItemStack bow = ItemStackBuilder.of(Items.BOW)
                .setUnbreakable()
                .build();
        player.getInventory().setItem(0, bow);
        player.getInventory().add(17, new ItemStack(Items.ARROW, 1));
        player.setExperiencePoints(0);
        player.setExperienceLevels(1);
    }

    public void resetWaitingPlayer(ServerPlayer player, GameType gameMode) {
        player.setGameMode(gameMode);
        player.getInventory().clearContent();
        player.removeAllEffects();
    }

    public void spawnPlayer(ServerPlayer player) {
        ServerLevel world = this.world;

        Vec3 pos = this.map.getSpawn(player.getRandom().nextInt(this.map.spawns.size())).centerBottom();
        player.teleportTo(world, pos.x(), pos.y(), pos.z(), Set.of(), 0.0F, 0.0F, false);
    }
}
