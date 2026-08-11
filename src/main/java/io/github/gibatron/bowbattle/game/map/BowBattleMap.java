package io.github.gibatron.bowbattle.game.map;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.plasmid.api.game.world.generator.TemplateChunkGenerator;

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
