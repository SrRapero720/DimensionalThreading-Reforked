package me.srrapero720.dimthread.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.srrapero720.dimthread.DimConfig;
import me.srrapero720.dimthread.DimThread;
import me.srrapero720.dimthread.thread.ThreadPool;
import me.srrapero720.dimthread.util.CrashInfo;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

@Mixin(value = MinecraftServer.class, priority = 1010)
public abstract class MinecraftServerMixin {
    @Shadow private int tickCount;
    @Shadow private PlayerList playerList;
    @Shadow public abstract Iterable<ServerLevel> getAllLevels();

    @Unique
    private final AtomicReference<CrashInfo> dimthreads$initialException = new AtomicReference<>();

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

        pool.execute(this.getAllLevels().iterator(), level -> {
            DimThread.attach(Thread.currentThread(), level);

            if (this.tickCount % 20 == 0) {
                ClientboundSetTimePacket timeUpdatePacket = new ClientboundSetTimePacket(
                    level.getGameTime(), level.getDayTime(),
                    level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT));

                this.playerList.broadcastAll(timeUpdatePacket, level.dimension());
            }

            DimThread.swapThreadsAndRun(() -> {
                EventHooks.fireLevelTickPre(level, shouldKeepTicking);
                try {
                    level.tick(shouldKeepTicking);
                } catch (Throwable throwable) {
                    crash.set(new CrashInfo(level, throwable));
                }
                EventHooks.fireLevelTickPost(level, shouldKeepTicking);
            }, level, level.getChunkSource());
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
     * Shutdown all threadpools when the server stops.
     * Prevent server hang when stopping the server.
     */
    @Inject(method = "stopServer", at = @At("HEAD"))
    public void shutdownThreadpool(CallbackInfo ci) {
        DimThread.MANAGER.threadPools.forEach((server, pool) -> pool.shutdown());
        DimThread.MANAGER.clear();
    }
}