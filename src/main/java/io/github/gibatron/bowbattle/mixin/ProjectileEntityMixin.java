package io.github.gibatron.bowbattle.mixin;

import io.github.gibatron.bowbattle.BowBattle;
import java.util.UUID;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nucleoid.plasmid.api.game.GameSpaceManager;
import xyz.nucleoid.stimuli.event.EventResult;

@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntityMixin extends Entity {

    @Shadow
    private UUID ownerUuid;

    public ProjectileEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "onBlockHit", at = @At("HEAD"))
    private void onBlockHit(BlockHitResult blockHitResult, CallbackInfo ci) {
        var world = this.getWorld();
        var gameSpace = GameSpaceManager.get().byWorld(world);
        if (
            gameSpace != null &&
            gameSpace
                .getBehavior()
                .testRule(BowBattle.BOW_GRAPPLES_SOUL_LANTERNS) ==
            EventResult.ALLOW
        ) {
            if (
                world.getBlockState(blockHitResult.getBlockPos()).getBlock() ==
                Blocks.SOUL_LANTERN
            ) {
                if (ownerUuid != null && world instanceof ServerWorld) {
                    Entity owner = ((ServerWorld) world).getEntity(ownerUuid);
                    if (owner != null) {
                        owner.setVelocity(
                            blockHitResult
                                .getPos()
                                .subtract(owner.getPos())
                                .normalize()
                                .multiply(1)
                        );
                        owner.velocityModified = true;
                        ((PlayerEntity) owner).addStatusEffect(
                                new StatusEffectInstance(
                                    StatusEffects.LEVITATION,
                                    20,
                                    254,
                                    true,
                                    false
                                )
                            );
                        remove(RemovalReason.DISCARDED);
                    }
                }
            } else {
                remove(RemovalReason.DISCARDED);
            }
        }
    }
}
