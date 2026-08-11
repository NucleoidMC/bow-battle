package io.github.gibatron.bowbattle.game;

import io.github.gibatron.bowbattle.BowBattle;
import io.github.gibatron.bowbattle.game.map.BowBattleMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.apache.commons.lang3.RandomStringUtils;
import xyz.nucleoid.plasmid.api.game.GameCloseReason;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.PlayerSet;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.api.util.PlayerRef;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;
import xyz.nucleoid.stimuli.event.projectile.ArrowFireEvent;

public class BowBattleActive {

    private final BowBattleConfig config;

    public final ServerWorld world;
    public final GameSpace gameSpace;
    private final BowBattleMap gameMap;

    private final Object2ObjectMap<PlayerRef, BowBattlePlayer> participants;
    private final BowBattleSpawnLogic spawnLogic;
    private final BowBattleStageManager stageManager;
    private final boolean ignoreWinState;
    private final BowBattleTimerBar timerBar;
    private final Team scoreboardTeam;

    private BowBattleActive(
        GameSpace gameSpace,
        ServerWorld world,
        BowBattleMap map,
        GlobalWidgets widgets,
        BowBattleConfig config,
        Set<PlayerRef> participants
    ) {
        this.world = world;
        this.gameSpace = gameSpace;
        this.config = config;
        this.gameMap = map;
        this.spawnLogic = new BowBattleSpawnLogic(this.world, map);
        this.participants = new Object2ObjectOpenHashMap<>();

        Scoreboard scoreboard = gameSpace.getServer().getScoreboard();
        scoreboardTeam = scoreboard.addTeam(
            RandomStringUtils.randomAlphanumeric(16)
        );
        scoreboardTeam.setNameTagVisibilityRule(
            AbstractTeam.VisibilityRule.NEVER
        );
        scoreboardTeam.setCollisionRule(AbstractTeam.CollisionRule.NEVER);

        for (PlayerRef player : participants) {
            scoreboard.addScoreHolderToTeam(
                player.getEntity(this.world).getName().getString(),
                scoreboardTeam
            );
            this.participants.put(player, new BowBattlePlayer());
        }

        this.stageManager = new BowBattleStageManager();
        this.ignoreWinState = this.participants.size() <= 1;
        this.timerBar = new BowBattleTimerBar(widgets);
    }

    public static void open(
        GameSpace gameSpace,
        ServerWorld world,
        BowBattleMap map,
        BowBattleConfig config
    ) {
        gameSpace.setActivity(activity -> {
            var widgets = GlobalWidgets.addTo(activity);

            Set<PlayerRef> participants = gameSpace
                .getPlayers()
                .stream()
                .map(PlayerRef::of)
                .collect(Collectors.toSet());

            var active = new BowBattleActive(
                gameSpace,
                world,
                map,
                widgets,
                config,
                participants
            );

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

            activity.listen(GamePlayerEvents.ACCEPT, offer ->
                offer.teleport(
                    active.world,
                    active.gameMap.getSpawn(0).center()
                )
            );
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
            ref.ifOnline(this.world, this::spawnParticipant);
        }
        this.stageManager.onOpen(this.world.getTime(), this.config);
    }

    private void onClose() {
        gameSpace.getServer().getScoreboard().removeTeam(scoreboardTeam);
    }

    private void addPlayer(ServerPlayerEntity player) {
        if (!this.participants.containsKey(PlayerRef.of(player))) {
            this.spawnSpectator(player);
        }
    }

    private void removePlayer(ServerPlayerEntity player) {
        this.participants.remove(PlayerRef.of(player));
    }

    private EventResult onPlayerDamage(
        ServerPlayerEntity player,
        DamageSource source,
        float amount
    ) {
        if (
            source.isIn(DamageTypeTags.IS_PROJECTILE) &&
            source.getAttacker() != player
        ) {
            if (source.getAttacker() != null) {
                ((PlayerEntity) source.getAttacker()).playSound(
                        SoundEvents.ENTITY_ARROW_HIT_PLAYER,
                        1f,
                        1f
                    );
                gameSpace
                    .getPlayers()
                    .sendMessage(
                        Text.literal(
                            String.format(
                                "☠ - %s was shot by %s",
                                player.getDisplayName().getString(),
                                source
                                    .getAttacker()
                                    .getDisplayName()
                                    .getString()
                            )
                        ).formatted(Formatting.GRAY)
                    );
                participants.get(
                    PlayerRef.of((ServerPlayerEntity) source.getAttacker())
                ).kills += 1;
            }
            //Thanks Potatoboy9999 ;)
            for (int i = 0; i < 75; i++) {
                this.world.spawnParticles(
                        ParticleTypes.FIREWORK,
                        player.getPos().getX(),
                        player.getPos().getY() + 1.0f,
                        player.getPos().getZ(),
                        1,
                        ((player.getRandom().nextFloat() * 2.0f) - 1.0f) *
                        0.35f,
                        ((player.getRandom().nextFloat() * 2.0f) - 1.0f) *
                        0.35f,
                        ((player.getRandom().nextFloat() * 2.0f) - 1.0f) *
                        0.35f,
                        0.1
                    );
            }
            this.spawnParticipant(player);
        }
        return EventResult.DENY;
    }

    private EventResult onPlayerDeath(
        ServerPlayerEntity player,
        DamageSource source
    ) {
        this.spawnParticipant(player);
        return EventResult.DENY;
    }

    private EventResult onPlayerFire(
        ServerPlayerEntity player,
        ItemStack bowStack,
        ArrowItem arrowItem,
        int remaining,
        PersistentProjectileEntity projectile
    ) {
        projectile.pickupType =
            PersistentProjectileEntity.PickupPermission.DISALLOWED;
        Vec3d velocity = projectile.getVelocity();
        projectile.setVelocity(velocity.x, velocity.y, velocity.z, 5F, 0.0F);
        projectile.setNoGravity(true);
        player.experienceLevel -= 1;
        return EventResult.PASS;
    }

    private void spawnParticipant(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);
        this.spawnLogic.spawnPlayer(player);
    }

    private void spawnSpectator(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.SPECTATOR);
        this.spawnLogic.spawnPlayer(player);
    }

    private void tick() {
        long time = this.world.getTime();

        BowBattleStageManager.IdleTickResult result =
            this.stageManager.tick(time, gameSpace);

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

        this.timerBar.update(
                this.stageManager.finishTime - time,
                this.config.timeLimitSecs() * 20L
            );

        PlayerSet players = this.gameSpace.getPlayers();
        for (ServerPlayerEntity player : players) {
            if (!player.isSpectator()) {
                boolean usingBow =
                    player.getActiveItem().getItem() == Items.BOW;
                player.sendMessage(
                    Text.literal(
                        String.format(
                            "Kills: %s",
                            participants.get(PlayerRef.of(player)).kills
                        )
                    ).formatted(Formatting.WHITE, Formatting.BOLD),
                    true
                );
                if (player.experienceLevel < 5 && !usingBow) {
                    if (player.age % 4 == 0) player.addExperience(1);
                }
                //player.setNoGravity(usingBow);
                if (
                    usingBow &&
                    player
                        .getInventory()
                        .contains(Items.ARROW.getDefaultStack()) &&
                    player.getInventory().getStack(17).getCount() > 0
                ) {
                    applyHoverLevitation(player);
                    player.setVelocity(0, 0, 0);
                    player.velocityModified = true;
                } else {
                    if (
                        player.hasStatusEffect(StatusEffects.LEVITATION) &&
                        player
                            .getStatusEffect(StatusEffects.LEVITATION)
                            .getAmplifier() !=
                        254
                    ) player.removeStatusEffect(StatusEffects.LEVITATION);
                }

                if (
                    !player.hasStatusEffect(StatusEffects.INVISIBILITY) &&
                    !player.hasStatusEffect(StatusEffects.GLOWING)
                ) {
                    player.addStatusEffect(
                        new StatusEffectInstance(
                            StatusEffects.GLOWING,
                            StatusEffectInstance.INFINITE,
                            1,
                            true,
                            false
                        )
                    );
                }
            }
        }
    }

    private void applyHoverLevitation(ServerPlayerEntity player) {
        if (
            !player.hasStatusEffect(StatusEffects.LEVITATION) ||
            player.getStatusEffect(StatusEffects.LEVITATION).getAmplifier() ==
            254
        ) {
            player.removeStatusEffect(StatusEffects.LEVITATION);
            player.addStatusEffect(
                new StatusEffectInstance(
                    StatusEffects.LEVITATION,
                    StatusEffectInstance.INFINITE,
                    -1,
                    true,
                    false
                )
            );
        }
    }

    private void broadcastWin(WinResult result) {
        ServerPlayerEntity winningPlayer = result.winningPlayer();

        Text message;
        if (winningPlayer != null) {
            message = Text.literal("★ - ").append(
                winningPlayer
                    .getDisplayName()
                    .copy()
                    .append(
                        " has won the game by getting " +
                        participants.get(PlayerRef.of(winningPlayer)).kills +
                        " kills!"
                    )
                    .formatted(Formatting.GOLD)
            );
        } else {
            message = Text.literal("The game ended, but nobody won!").formatted(
                Formatting.GOLD
            );
        }

        PlayerSet players = this.gameSpace.getPlayers();
        players.sendMessage(message);
        players.playSound(SoundEvents.ENTITY_VILLAGER_YES);
    }

    private WinResult checkWinResult() {
        // for testing purposes: don't end the game if we only ever had one participant
        if (this.ignoreWinState) {
            return WinResult.no();
        }

        PlayerRef best = null;
        for (Map.Entry<
            PlayerRef,
            BowBattlePlayer
        > entry : participants.entrySet()) {
            if (best == null) best = entry.getKey();
            else if (
                participants.get(best).kills < entry.getValue().kills
            ) best = entry.getKey();
        }
        if (best != null) return WinResult.win(best.getEntity(this.world));
        return WinResult.no();
    }

    record WinResult(ServerPlayerEntity winningPlayer, boolean win) {
        static WinResult no() {
            return new WinResult(null, false);
        }

        static WinResult win(ServerPlayerEntity player) {
            return new WinResult(player, true);
        }
    }
}
