package me.srrapero720.dimthread.mixin.impl;

import me.srrapero720.dimthread.DimThread;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Level.class)
public class LevelMixin {
    @Shadow public Thread thread;

    /**
     * Thread check was done to prevent deadlocks loading multiple chunks of async task.
     * Check is redundant when a Dimthreads thread gets stuff from other Dimthreads thread (unless)
     */
    @Redirect(method = "getBlockEntity", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;currentThread()Ljava/lang/Thread;"))
    private Thread recallForDimthreads() {
        Thread t = Thread.currentThread();
        Thread owner = this.thread; // read once, it can change while another dimension ticks
        // TODO: this is not appropriate,
        //  it requires a queue of dimthreads calling this considering how chunkloading works
        return DimThread.owns(t) && DimThread.owns(owner) ? owner : t; // mock the check
    }
}
