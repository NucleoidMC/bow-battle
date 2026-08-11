package io.github.gibatron.bowbattle.game.map;

import io.github.gibatron.bowbattle.BowBattle;
import net.minecraft.server.MinecraftServer;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.MapTemplateSerializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public record BowBattleMapGenerator(BowBattleMapConfig config) {

    public BowBattleMap create(MinecraftServer server) {
        MapTemplate template = MapTemplate.createEmpty();
        try {
            template = MapTemplateSerializer.loadFromResource(server, this.config.id());
        } catch (IOException e) {
            BowBattle.LOGGER.error("Failed to load map template", e);
        }
        List<BlockBounds> spawns = new ArrayList<>();
        template.getMetadata().getRegions("spawn").forEach(x -> spawns.add(x.getBounds()));
        return new BowBattleMap(template, spawns);
    }
}
