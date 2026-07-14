package io.github.gibatron.bowbattle.game.map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.chunk.ChunkGenerator;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.plasmid.api.game.level.generator.TemplateChunkGenerator;

import java.util.List;

public class BowBattleMap {
    private final MapTemplate template;
    public List<BlockBounds> spawns;

    public BowBattleMap(MapTemplate template, List<BlockBounds> spawns) {
        this.template = template;
        this.spawns = spawns;
    }

    public BlockBounds getSpawn(int index) {
        return this.spawns.get(index);
    }

    public ChunkGenerator asGenerator(MinecraftServer server) {
        return new TemplateChunkGenerator(server, this.template);
    }
}
