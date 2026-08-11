package io.github.gibatron.bowbattle;

import io.github.gibatron.bowbattle.game.BowBattleConfig;
import io.github.gibatron.bowbattle.game.BowBattleWaiting;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.nucleoid.plasmid.api.game.GameType;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;

public class BowBattle implements ModInitializer {

    public static final String ID = "bowbattle";
    public static final Logger LOGGER = LogManager.getLogger(ID);

    public static GameRuleType BOW_GRAPPLES_SOUL_LANTERNS =
        GameRuleType.create();
    public static GameRuleType BOW_SLOW_MO = GameRuleType.create();
    public static GameRuleType XP_RESTOCKS_ARROWS = GameRuleType.create();

    public static final GameType<BowBattleConfig> TYPE = GameType.register(
        Identifier.of(ID, "bowbattle"),
        BowBattleConfig.CODEC,
        BowBattleWaiting::open
    );

    @Override
    public void onInitialize() {}
}
