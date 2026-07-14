package io.github.gibatron.bowbattle.game;

import io.github.gibatron.bowbattle.game.map.BowBattleMap;
import io.github.gibatron.bowbattle.game.map.BowBattleMapGenerator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.plasmid.api.game.GameOpenContext;
import xyz.nucleoid.plasmid.api.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.api.game.GameResult;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptor;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptorResult;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;

public class BowBattleWaiting {
    private final GameSpace gameSpace;
    private final ServerLevel level;
    private final BowBattleMap map;
    private final BowBattleConfig config;
    private final BowBattleSpawnLogic spawnLogic;

    private BowBattleWaiting(GameSpace gameSpace, ServerLevel level, BowBattleMap map, BowBattleConfig config) {
        this.level = level;
        this.gameSpace = gameSpace;
        this.map = map;
        this.config = config;
        this.spawnLogic = new BowBattleSpawnLogic(this.level, map);
    }

    public static GameOpenProcedure open(GameOpenContext<BowBattleConfig> context) {
        BowBattleConfig config = context.config();
        BowBattleMapGenerator generator = new BowBattleMapGenerator(config.map());
        BowBattleMap map = generator.create(context.server());

        RuntimeLevelConfig levelConfig = new RuntimeLevelConfig()
                .setGenerator(map.asGenerator(context.server()));

        return context.openWithLevel(levelConfig, (game, level) -> {
            GameWaitingLobby.addTo(game, config.players());

            var waiting = new BowBattleWaiting(game.getGameSpace(), level, map, context.config());

            game.deny(GameRuleType.PVP);
            game.deny(GameRuleType.FALL_DAMAGE);
            game.deny(GameRuleType.HUNGER);
            game.deny(GameRuleType.THROW_ITEMS);

            game.listen(GamePlayerEvents.OFFER, JoinOffer::acceptParticipants);
            game.listen(GamePlayerEvents.ACCEPT, waiting::onAccept);
            game.listen(GameActivityEvents.REQUEST_START, waiting::requestStart);
            game.listen(GamePlayerEvents.ADD, waiting::addPlayer);
            game.listen(PlayerDamageEvent.EVENT, (player, source, amount) -> EventResult.DENY);
        });
    }

    private JoinAcceptorResult onAccept(JoinAcceptor acceptor) {
        return acceptor.teleport(level, map.getSpawn(0) != null ? map.getSpawn(0).center() : new Vec3(0, 256, 0));
    }

    private GameResult requestStart() {
        BowBattleActive.open(this.gameSpace, this.level, this.map, this.config);
        return GameResult.ok();
    }

    private void addPlayer(ServerPlayer player) {
        this.spawnPlayer(player);
    }

    private void spawnPlayer(ServerPlayer player) {
        this.spawnLogic.resetWaitingPlayer(player, GameType.ADVENTURE);
        this.spawnLogic.spawnPlayer(player);
    }
}
