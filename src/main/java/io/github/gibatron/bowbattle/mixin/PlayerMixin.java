package io.github.gibatron.bowbattle.mixin;

import io.github.gibatron.bowbattle.BowBattle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nucleoid.plasmid.api.game.GameSpaceManager;
import xyz.nucleoid.stimuli.event.EventResult;

@Mixin(Player.class)
public abstract class PlayerMixin extends Entity {

    @Shadow @Final public Inventory inventory;

    public PlayerMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Inject(method = "giveExperienceLevels", at = @At("HEAD"))
    private void addExperienceLevels(int levels, CallbackInfo ci) {
        var gameSpace = GameSpaceManager.get().byLevel(this.level());
        if (gameSpace != null && gameSpace.getBehavior().testRule(BowBattle.XP_RESTOCKS_ARROWS) == EventResult.ALLOW)
            this.inventory.add(17, new ItemStack(Items.ARROW, 1));
    }
}
