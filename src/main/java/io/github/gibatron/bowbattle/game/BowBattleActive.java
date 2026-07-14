package io.github.gibatron.bowbattle.game;

import com.google.common.collect.ImmutableSet;
import io.github.gibatron.bowbattle.BowBattle;
import io.github.gibatron.bowbattle.game.map.BowBattleMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.scores.Team;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import org.apache.commons.lang3.RandomStringUtils;
import xyz.nucleoid.plasmid.api.game.GameCloseReason;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.player.PlayerSet;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.api.util.PlayerRef;
import xyz.nucleoid.plasmid.api.util.PlayerUtil;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;
import xyz.nucleoid.stimuli.event.projectile.ArrowFireEvent;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class BowBattleActive {
    private final BowBattleConfig config;

    public final ServerLevel level;
    public final GameSpace gameSpace;
    private final BowBattleMap gameMap;

    private final Object2ObjectMap<PlayerRef, BowBattlePlayer> participants;
    private final BowBattleSpawnLogic spawnLogic;
    private final BowBattleStageManager stageManager;
    private final boolean ignoreWinState;
    private final BowBattleTimerBar timerBar;
    private final PlayerTeam scoreboardTeam;

    private BowBattleActive(GameSpace gameSpace, ServerLevel level, BowBattleMap map, GlobalWidgets widgets, BowBattleConfig config, Set<PlayerRef> participants) {
        this.level = level;
        this.gameSpace = gameSpace;
        this.config = config;
        this.gameMap = map;
        this.spawnLogic = new BowBattleSpawnLogic(this.level, map);
        this.participants = new Object2ObjectOpenHashMap<>();

        ServerScoreboard scoreboard = gameSpace.getServer().getScoreboard();
        scoreboardTeam = scoreboard.addPlayerTeam(RandomStringUtils.randomAlphanumeric(16));
        scoreboardTeam.setNameTagVisibility(Team.Visibility.NEVER);
        scoreboardTeam.setCollisionRule(Team.CollisionRule.NEVER);

        for (PlayerRef player : participants) {
            scoreboard.addPlayerToTeam(player.getEntity(this.level).getName().getString(), scoreboardTeam);
            this.participants.put(player, new BowBattlePlayer());
        }

        this.stageManager = new BowBattleStageManager();
        this.ignoreWinState = this.participants.size() <= 1;
        this.timerBar = new BowBattleTimerBar(widgets);
    }

    public static void open(GameSpace gameSpace, ServerLevel level, BowBattleMap map, BowBattleConfig config) {
        gameSpace.setActivity(activity -> {
            var widgets = GlobalWidgets.addTo(activity);

            Set<PlayerRef> participants = gameSpace.getPlayers().stream()
                    .map(PlayerRef::of)
                    .collect(Collectors.toSet());

            var active = new BowBattleActive(gameSpace, level, map, widgets, config, participants);

            activity.deny(GameRuleType.CRAFTING);
            activity.deny(GameRuleType.PORTALS);
            activity.deny(GameRuleType.BLOCK_DROPS);
            activity.deny(GameRuleType.FALL_DAMAGE);
            activity.deny(GameRuleType.HUNGER);
            activity.deny(GameRuleType.THROW_ITEMS);
            activity.deny(GameRuleType.UNSTABLE_TNT);
            activity.deny(GameRuleType.MODIFY_INVENTORY);

            activity.allow(GameRuleType.PVP);
            activity.allow(GameRuleType.USE_ITEMS);
            activity.allow(GameRuleType.INTERACTION);
            activity.allow(BowBattle.BOW_SLOW_MO);
            activity.allow(BowBattle.BOW_GRAPPLES_SOUL_LANTERNS);
            activity.allow(BowBattle.XP_RESTOCKS_ARROWS);

            activity.listen(GameActivityEvents.ENABLE, active::onOpen);
            activity.listen(GameActivityEvents.DISABLE, active::onClose);
            activity.listen(GamePlayerEvents.OFFER, JoinOffer::acceptParticipants);
            activity.listen(GamePlayerEvents.ACCEPT, acceptor -> acceptor.teleport(active.level, active.gameMap.getSpawn(0).center()));
            activity.listen(GamePlayerEvents.ADD, active::addPlayer);
            activity.listen(GamePlayerEvents.REMOVE, active::removePlayer);

            activity.listen(GameActivityEvents.TICK, active::tick);

            activity.listen(PlayerDeathEvent.EVENT, active::onPlayerDeath);
            activity.listen(PlayerDamageEvent.EVENT, active::onPlayerDamage);
            activity.listen(ArrowFireEvent.EVENT, active::onPlayerFire);
        });
    }

    private void onOpen() {
        for (PlayerRef ref : this.participants.keySet()) {
            ref.ifOnline(this.level, this::spawnParticipant);
        }
        this.stageManager.onOpen(this.level.getGameTime(), this.config);
    }

    private void onClose() {
        gameSpace.getServer().getScoreboard().removePlayerTeam(scoreboardTeam);
    }

    private void addPlayer(ServerPlayer player) {
        if (!this.participants.containsKey(PlayerRef.of(player))) {
            this.spawnSpectator(player);
        }
    }

    private void removePlayer(ServerPlayer player) {
        this.participants.remove(PlayerRef.of(player));
    }

    private EventResult onPlayerDamage(ServerPlayer player, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_PROJECTILE) && source.getEntity() != player) {
            if (source.getEntity() != null) {
                PlayerUtil.playSoundToPlayer(((ServerPlayer) source.getEntity()), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1f, 1f);
                gameSpace.getPlayers().sendMessage(Component.literal(String.format("☠ - %s was shot by %s", player.getDisplayName().getString(), source.getEntity().getDisplayName().getString())).withStyle(ChatFormatting.GRAY));
                participants.get(PlayerRef.of((ServerPlayer) source.getEntity())).kills += 1;
            }
            //Thanks Potatoboy9999 ;)
            for (int i = 0; i < 75; i++) {
                this.level.sendParticles(
                        ParticleTypes.FIREWORK,
                        player.position().x(),
                        player.position().y() + 1.0f,
                        player.position().z(),
                        1,
                        ((player.getRandom().nextFloat() * 2.0f) - 1.0f) * 0.35f,
                        ((player.getRandom().nextFloat() * 2.0f) - 1.0f) * 0.35f,
                        ((player.getRandom().nextFloat() * 2.0f) - 1.0f) * 0.35f,
                        0.1
                );
            }
            this.spawnParticipant(player);
        }
        return EventResult.DENY;
    }

    private EventResult onPlayerDeath(ServerPlayer player, DamageSource source) {
        this.spawnParticipant(player);
        return EventResult.DENY;
    }

    private EventResult onPlayerFire(ServerPlayer player, ItemStack bowStack, ArrowItem arrowItem, int remaining, AbstractArrow projectile) {
        projectile.pickup = AbstractArrow.Pickup.DISALLOWED;
        Vec3 velocity = projectile.getDeltaMovement();
        projectile.shoot(velocity.x, velocity.y, velocity.z, 5F, 0.0F);
        projectile.setNoGravity(true);
        player.experienceLevel -= 1;
        return EventResult.PASS;
    }

    private void spawnParticipant(ServerPlayer player) {
        this.spawnLogic.resetPlayer(player, GameType.ADVENTURE);
        this.spawnLogic.spawnPlayer(player);
    }

    private void spawnSpectator(ServerPlayer player) {
        this.spawnLogic.resetPlayer(player, GameType.SPECTATOR);
        this.spawnLogic.spawnPlayer(player);
    }

    private void tick() {
        long time = this.level.getGameTime();

        BowBattleStageManager.IdleTickResult result = this.stageManager.tick(time, gameSpace);

        switch (result) {
            case CONTINUE_TICK:
                break;
            case TICK_FINISHED:
                return;
            case GAME_FINISHED:
                this.broadcastWin(this.checkWinResult());
                return;
            case GAME_CLOSED:
                this.gameSpace.close(GameCloseReason.FINISHED);
                return;
        }

        this.timerBar.update(this.stageManager.finishTime - time, this.config.timeLimitSecs() * 20L);

        PlayerSet players = this.gameSpace.getPlayers();
        for (ServerPlayer player : players) {
            if (!player.isSpectator()) {
                boolean usingBow = player.getUseItem().getItem() == Items.BOW;
                player.sendSystemMessage(Component.literal(String.format("Kills: %s", participants.get(PlayerRef.of(player)).kills)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), true);
                if (player.experienceLevel < 5 && !usingBow) {
                    if (player.tickCount % 4 == 0)
                        player.giveExperiencePoints(1);
                }
                player.setNoGravity(usingBow);

                if (usingBow && player.getInventory().contains(Items.ARROW.getDefaultInstance()) && player.getInventory().getItem(17).getCount() > 0) {
                    //applyHoverLevitation(player);
                    // Set X and Y as relative so it will send 0 change when we pass yaw (yaw - yaw = 0) and pitch
                    Set<Relative> flags = ImmutableSet.of(Relative.X_ROT, Relative.Y_ROT);

                    // Teleport without changing the pitch and yaw
                    player.teleportTo(player.level(), player.getX(), player.getY(), player.getZ(), flags, 0, 0, false);
                } else {
                    if (player.hasEffect(MobEffects.LEVITATION) && player.getEffect(MobEffects.LEVITATION).getAmplifier() != 254)
                        player.removeEffect(MobEffects.LEVITATION);
                }

                if (!player.hasEffect(MobEffects.INVISIBILITY) && !player.hasEffect(MobEffects.GLOWING)) {
                    player.addEffect(new MobEffectInstance(
                            MobEffects.GLOWING,
                            MobEffectInstance.INFINITE_DURATION,
                            1,
                            true,
                            false
                    ));
                }
            }
        }
    }

    private void applyHoverLevitation(ServerPlayer player) {
        if (!player.hasEffect(MobEffects.LEVITATION) || player.getEffect(MobEffects.LEVITATION).getAmplifier() == 254) {
            player.removeEffect(MobEffects.LEVITATION);
            player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, MobEffectInstance.INFINITE_DURATION, -2, true, false));
        }
    }

    private void broadcastWin(WinResult result) {
        ServerPlayer winningPlayer = result.winningPlayer();

        Component message;
        if (winningPlayer != null) {
            message = Component.literal("★ - ").append(winningPlayer.getDisplayName().copy().append(" has won the game by getting " + participants.get(PlayerRef.of(winningPlayer)).kills + " kills!").withStyle(ChatFormatting.GOLD));
        } else {
            message = Component.literal("The game ended, but nobody won!").withStyle(ChatFormatting.GOLD);
        }

        PlayerSet players = this.gameSpace.getPlayers();
        players.sendMessage(message);
        players.playSound(SoundEvents.VILLAGER_YES);
    }

    private WinResult checkWinResult() {
        // for testing purposes: don't end the game if we only ever had one participant
        if (this.ignoreWinState) {
            return WinResult.no();
        }

        PlayerRef best = null;
        for (Map.Entry<PlayerRef, BowBattlePlayer> entry : participants.entrySet())
        {
            if (best == null)
                best = entry.getKey();
            else if (participants.get(best).kills < entry.getValue().kills)
                best = entry.getKey();
        }
        if (best != null)
            return WinResult.win(best.getEntity(this.level));
        return WinResult.no();
    }

    record WinResult(ServerPlayer winningPlayer, boolean win) {

        static WinResult no() {
            return new WinResult(null, false);
        }

        static WinResult win(ServerPlayer player) {
            return new WinResult(player, true);
        }
    }
}
