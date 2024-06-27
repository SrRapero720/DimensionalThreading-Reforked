package me.srrapero720.dimthread.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import me.srrapero720.dimthread.DimThread;

import javax.annotation.Nullable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow @Nullable public abstract Entity changeDimension(DimensionTransition pTransition);

    /**
     * Schedules moving entities between dimensions to the server thread. Once all the world finish ticking,
     * {@code moveToWorld()} is processed in a safe manner avoiding concurrent modification exceptions.
     * <p>
     * For example, the entity list is not thread-safe and modifying it from multiple threads will cause
     * a crash. Additionally, loading chunks from another thread will cause a deadlock in the server chunk manager.
     */
    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true, remap = false)
    public void moveToWorld(DimensionTransition dimensionTransition, CallbackInfoReturnable<Entity> cir) {
        if (!DimThread.MANAGER.isActive(dimensionTransition.newLevel().getServer())) return;

        if (DimThread.owns(Thread.currentThread())) {
            dimensionTransition.newLevel().getServer().execute(() -> this.changeDimension(dimensionTransition));
            cir.setReturnValue(null);
        }
    }
}