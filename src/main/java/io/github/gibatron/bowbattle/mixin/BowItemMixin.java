package io.github.gibatron.bowbattle.mixin;

import io.github.gibatron.bowbattle.BowBattle;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.plasmid.api.game.GameSpaceManager;
import xyz.nucleoid.stimuli.event.EventResult;

@Mixin(BowItem.class)
public abstract class BowItemMixin {
    @Inject(method = "use", at = @At("HEAD"))
    private void use(Level level, Player user, InteractionHand hand, CallbackInfoReturnable<ItemStack> cir) {
        var gameSpace = GameSpaceManager.get().byLevel(level);
        if (gameSpace != null && gameSpace.getBehavior().testRule(BowBattle.BOW_SLOW_MO) == EventResult.ALLOW) {
            if (user.getInventory().getItem(17).getCount() > 0) {
                user.setDeltaMovement(0, 0, 0);
                user.needsSync = true;
            }
        }
    }
}
