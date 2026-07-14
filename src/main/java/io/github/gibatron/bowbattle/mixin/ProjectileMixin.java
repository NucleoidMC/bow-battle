package io.github.gibatron.bowbattle.mixin;

import io.github.gibatron.bowbattle.BowBattle;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nucleoid.plasmid.api.game.GameSpaceManager;
import xyz.nucleoid.stimuli.event.EventResult;


@Mixin(Projectile.class)
public abstract class ProjectileMixin extends Entity {
    @Shadow
    public abstract @Nullable Entity getOwner();

    public ProjectileMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Inject(method = "onHitBlock", at = @At("HEAD"))
    private void onBlockHit(BlockHitResult blockHitResult, CallbackInfo ci) {
        var level = this.level();
        var gameSpace = GameSpaceManager.get().byLevel(level);
        if (gameSpace != null && gameSpace.getBehavior().testRule(BowBattle.BOW_GRAPPLES_SOUL_LANTERNS) == EventResult.ALLOW) {
            if (level.getBlockState(blockHitResult.getBlockPos()).getBlock() == Blocks.SOUL_LANTERN) {
                Entity owner = this.getOwner();
                if (level instanceof ServerLevel) {
                    if (owner != null) {
                        owner.setDeltaMovement(blockHitResult.getLocation().subtract(owner.position()).normalize().scale(1));
                        owner.needsSync = true;
                        ((Player) owner).addEffect(new MobEffectInstance(
                                MobEffects.LEVITATION,
                                20,
                                254,
                                true,
                                false
                        ));
                        remove(RemovalReason.DISCARDED);
                    }
                }
            } else {
                remove(RemovalReason.DISCARDED);
            }
        }
    }
}
