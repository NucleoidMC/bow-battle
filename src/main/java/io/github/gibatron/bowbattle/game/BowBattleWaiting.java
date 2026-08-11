package io.github.gibatron.bowbattle.game;

import io.github.gibatron.bowbattle.game.map.BowBattleMap;
import io.github.gibatron.bowbattle.game.map.BowBattleMapGenerator;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.plasmid.api.game.GameOpenContext;
import xyz.nucleoid.plasmid.api.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.api.game.GameResult;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;

public class BowBattleWaiting {

    private final GameSpace gameSpace;
    private final ServerWorld world;
    private final BowBattleMap map;
    private final BowBattleConfig config;
    private final BowBattleSpawnLogic spawnLogic;

    private BowBattleWaiting(
        GameSpace gameSpace,
        ServerWorld world,
        BowBattleMap map,
        BowBattleConfig config
    ) {
        this.world = world;
        this.gameSpace = gameSpace;
        this.map = map;
        this.config = config;
        this.spawnLogic = new BowBattleSpawnLogic(this.world, map);
    }

    public static GameOpenProcedure open(
        GameOpenContext<BowBattleConfig> context
    ) {
        BowBattleConfig config = context.config();
        BowBattleMapGenerator generator = new BowBattleMapGenerator(
            config.map()
        );
        BowBattleMap map = generator.create(context.server());

        RuntimeWorldConfig worldConfig = new RuntimeWorldConfig()
            .setGenerator(map.asGenerator(context.server()));

        return context.openWithWorld(worldConfig, (game, world) -> {
            GameWaitingLobby.addTo(game, config.players());

            var waiting = new BowBattleWaiting(
                game.getGameSpace(),
                world,
                map,
                context.config()
            );

            game.deny(GameRuleType.PVP);
            game.deny(GameRuleType.FALL_DAMAGE);
            game.deny(GameRuleType.HUNGER);
            game.deny(GameRuleType.THROW_ITEMS);

            game.listen(GamePlayerEvents.ACCEPT, player ->
                player.teleport(
                    world,
                    map.getSpawn(0) != null
                        ? map.getSpawn(0).center()
                        : new Vec3d(0, 256, 0)
                )
            );
            game.listen(
                GameActivityEvents.REQUEST_START,
                waiting::requestStart
            );
            game.listen(GamePlayerEvents.ADD, waiting::addPlayer);
            game.listen(PlayerDamageEvent.EVENT, (player, source, amount) ->
                EventResult.DENY
            );
        });
    }

    private GameResult requestStart() {
        BowBattleActive.open(this.gameSpace, this.world, this.map, this.config);
        return GameResult.ok();
    }

    private void addPlayer(ServerPlayerEntity player) {
        this.spawnPlayer(player);
    }

    private void spawnPlayer(ServerPlayerEntity player) {
        this.spawnLogic.resetWaitingPlayer(player, GameMode.ADVENTURE);
        this.spawnLogic.spawnPlayer(player);
    }
}
