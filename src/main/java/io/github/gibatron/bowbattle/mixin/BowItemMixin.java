package io.github.gibatron.bowbattle.mixin;

import io.github.gibatron.bowbattle.BowBattle;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.plasmid.api.game.GameSpaceManager;
import xyz.nucleoid.stimuli.event.EventResult;

@Mixin(BowItem.class)
public abstract class BowItemMixin {

    @Inject(method = "use", at = @At("HEAD"))
    private void use(
        World world,
        PlayerEntity user,
        Hand hand,
        CallbackInfoReturnable<ActionResult> cir
    ) {
        var gameSpace = GameSpaceManager.get().byWorld(world);
        if (
            gameSpace != null &&
            gameSpace.getBehavior().testRule(BowBattle.BOW_SLOW_MO) ==
            EventResult.ALLOW
        ) {
            if (user.getInventory().getStack(17).getCount() > 0) {
                user.setVelocity(0, 0, 0);
                user.velocityModified = true;
            }
        }
    }
}
