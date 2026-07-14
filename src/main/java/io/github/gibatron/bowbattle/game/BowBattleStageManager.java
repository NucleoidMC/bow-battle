package io.github.gibatron.bowbattle.game;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.player.PlayerSet;

import java.util.Set;

public class BowBattleStageManager {
    private long closeTime = -1;
    public long finishTime = -1;
    private long startTime = -1;
    private final Object2ObjectMap<ServerPlayer, FrozenPlayer> frozen;
    private boolean setSpectator = false;

    public BowBattleStageManager() {
        this.frozen = new Object2ObjectOpenHashMap<>();
    }

    public void onOpen(long time, BowBattleConfig config) {
        this.startTime = time - (time % 20) + (4 * 20) + 19;
        this.finishTime = this.startTime + (config.timeLimitSecs() * 20L);
    }

    public IdleTickResult tick(long time, GameSpace space) {
        // Game has finished. Wait a few seconds before finally closing the game.
        if (this.closeTime > 0) {
            if (time >= this.closeTime) {
                return IdleTickResult.GAME_CLOSED;
            }
            return IdleTickResult.TICK_FINISHED;
        }

        // Game hasn't started yet. Display a countdown before it begins.
        if (this.startTime > time) {
            this.tickStartWaiting(time, space);
            return IdleTickResult.TICK_FINISHED;
        }

        // Game has just finished. Transition to the waiting-before-close state.
        if (time > this.finishTime || space.getPlayers().isEmpty()) {
            if (!this.setSpectator) {
                this.setSpectator = true;
                for (ServerPlayer player : space.getPlayers()) {
                    player.setGameMode(GameType.SPECTATOR);
                }
            }

            this.closeTime = time + (5 * 20);

            return IdleTickResult.GAME_FINISHED;
        }

        return IdleTickResult.CONTINUE_TICK;
    }

    private void tickStartWaiting(long time, GameSpace space) {
        float sec_f = (this.startTime - time) / 20.0f;

        if (sec_f > 1) {
            for (ServerPlayer player : space.getPlayers()) {
                if (player.isSpectator()) {
                    continue;
                }

                FrozenPlayer state = this.frozen.computeIfAbsent(player, p -> new FrozenPlayer());

                if (state.lastPos == null) {
                    state.lastPos = player.position();
                }

                double destX = state.lastPos.x;
                double destY = state.lastPos.y;
                double destZ = state.lastPos.z;

                // Set X and Y as relative so it will send 0 change when we pass yaw (yaw - yaw = 0) and pitch
                Set<Relative> flags = ImmutableSet.of(Relative.X_ROT, Relative.Y_ROT);

                // Teleport without changing the pitch and yaw
                player.teleportTo(player.level(), destX, destY, destZ, flags, 0, 0, false);
            }
        }

        int sec = (int) Math.floor(sec_f) - 1;

        if ((this.startTime - time) % 20 == 0) {
            PlayerSet players = space.getPlayers();

            if (sec > 0) {
                players.showTitle(Component.literal(Integer.toString(sec)).withStyle(ChatFormatting.BOLD), 20);
                players.playSound(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            } else {
                players.showTitle(Component.literal("Go!").withStyle(ChatFormatting.BOLD), 20);
                players.playSound(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.0F, 2.0F);
            }
        }
    }

    public static class FrozenPlayer {
        public Vec3 lastPos;
    }

    public enum IdleTickResult {
        CONTINUE_TICK,
        TICK_FINISHED,
        GAME_FINISHED,
        GAME_CLOSED,
    }
}
