package me.srrapero720.dimthread.mixin.impl;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.srrapero720.dimthread.DimConfig;
import me.srrapero720.dimthread.DimThread;
import me.srrapero720.dimthread.thread.ThreadPool;
import me.srrapero720.dimthread.util.CrashInfo;
import net.minecraft.Util;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

@Mixin(value = MinecraftServer.class, priority = 1010)
public abstract class MinecraftServerMixin {
    @Shadow private int tickCount;
    @Shadow private PlayerList playerList;
    @Shadow public abstract Iterable<ServerLevel> getAllLevels();
    @Shadow public abstract Thread getRunningThread();
    @Shadow public abstract boolean isStopped();
    @Shadow private Map<ResourceKey<Level>, long[]> perWorldTickTimes;

    @Unique private final AtomicReference<CrashInfo> dimthreads$initialException = new AtomicReference<>();

    /**
     * Returns an empty iterator to stop {@code MinecraftServer#tickWorlds} from ticking
     * dimensions. This behaviour is overwritten below.
     *
     * @see MinecraftServerMixin#tickWorlds(BooleanSupplier, CallbackInfo)
     */
    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/MinecraftServer;getWorldArray()[Lnet/minecraft/server/level/ServerLevel;", remap = false))
    public ServerLevel[] tickWorlds(MinecraftServer instance, Operation<ServerLevel[]> original) {
        return DimThread.MANAGER.isActive((MinecraftServer) (Object) this) ? new ServerLevel[]{} : original.call(instance);
    }

    /**
     * Distributes world ticking over (at least) 3 worker threads (one for each dimension) and waits until
     * they are all complete.
     */
    @Inject(method = "tickChildren", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/MinecraftServer;getWorldArray()[Lnet/minecraft/server/level/ServerLevel;", remap = false))
    public void tickWorlds(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        if (!DimThread.MANAGER.isActive((MinecraftServer) (Object) this)) return;

        AtomicReference<CrashInfo> crash = new AtomicReference<>();
        ThreadPool pool = DimThread.getThreadPool((MinecraftServer) (Object) this);

        // Vanilla records these inside the loop emptied above, so '/neoforge tps' reported every dimension as
        // unloaded. perWorldTickTimes is a plain IdentityHashMap, so the buckets are created here on the
        // server thread and each worker only writes its own slot.
        int slot = this.tickCount % 100;
        for (ServerLevel level : this.getAllLevels()) {
            this.perWorldTickTimes.computeIfAbsent(level.dimension(), k -> new long[100]);
        }

        pool.execute(this.getAllLevels(), level -> {
            long tickStart = Util.getNanos();
            long[] tickTimes = this.perWorldTickTimes.get(level.dimension()); // bucket created above
            DimThread.attach(Thread.currentThread(), level);

            if (this.tickCount % 20 == 0) {
                ClientboundSetTimePacket timeUpdatePacket = new ClientboundSetTimePacket(
                    level.getGameTime(), level.getDayTime(),
                    level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT));

                this.playerList.broadcastAll(timeUpdatePacket, level.dimension());
            }

            DimThread.swapThreadsAndRun(() -> {
                // Only the events are serialized, see DimThread#TICK_EVENT_LOCK. The try covers them too: an
                // exception from a mod handler used to escape into the pool and kill the worker silently.
                try {
                    synchronized (DimThread.TICK_EVENT_LOCK) {
                        EventHooks.fireLevelTickPre(level, shouldKeepTicking);
                    }

                    level.tick(shouldKeepTicking);

                    synchronized (DimThread.TICK_EVENT_LOCK) {
                        EventHooks.fireLevelTickPost(level, shouldKeepTicking);
                    }
                } catch (Throwable throwable) {
                    crash.set(new CrashInfo(level, throwable));
                }
            }, level, level.getChunkSource());

            // Wall-clock, so dimensions running in parallel can each report more than the overall MSPT.
            if (tickTimes != null) tickTimes[slot] = Util.getNanos() - tickStart;
        });

        pool.awaitCompletion();

        if (crash.get() != null) {
            if (DimConfig.IGNORE_TICK_CRASH.get() && dimthreads$initialException.compareAndSet(null, crash.get())) {
                crash.get().report("Exception ticking world (asynchronously) -> EFFECTIVELY IGNORED");
            } else {
                crash.get().crash("Exception ticking world (asynchronously)");
            }
        }
    }

    /**
     * Mods guard their logic with {@code server.isSameThread()}, which compares against the single
     * {@code serverThread} field and so fails on our workers. That field cannot be swapped per dimension the
     * way {@code Level#thread} is, since every worker shares one server, so the check is widened instead.
     *
     * @see MinecraftServerMixin#alwaysDeferWorkerTasks(CallbackInfoReturnable)
     */
    public boolean isSameThread() {
        if (Thread.currentThread() == this.getRunningThread()) return true; // vanilla fast path
        // No isActive() check: this is far too hot to lock a map, and a worker only runs level-tick work.
        return DimThread.owns(Thread.currentThread());
    }

    /**
     * Vanilla routes scheduling through {@code isSameThread()}, so widening it above would make
     * {@code server.execute(task)} run inline on the worker and break {@code EntityMixin}'s deferral.
     * {@code !isStopped()} is what vanilla computes for any thread that is not the server thread.
     */
    @Inject(method = "scheduleExecutables", at = @At("HEAD"), cancellable = true)
    private void alwaysDeferWorkerTasks(CallbackInfoReturnable<Boolean> cir) {
        if (DimThread.owns(Thread.currentThread())) cir.setReturnValue(!this.isStopped());
    }

    /**
     * Shutdown all threadpools when the server stops.
     * Prevent server hang when stopping the server.
     */
    @Inject(method = "stopServer", at = @At("HEAD"))
    public void shutdownThreadpool(CallbackInfo ci) {
        DimThread.MANAGER.threadPools.forEach((server, pool) -> pool.shutdown());
        DimThread.MANAGER.clear();
    }
}