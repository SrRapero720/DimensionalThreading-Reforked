package me.srrapero720.dimthread.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.srrapero720.dimthread.DimThread;
import me.srrapero720.dimthread.thread.IMutableMainThread;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkSource;
import net.neoforged.fml.loading.FMLLoader;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ServerChunkCache.class, priority = 1001)
public abstract class ServerChunkManagerMixin extends ChunkSource implements IMutableMainThread {
	@Shadow public Thread mainThread;
	@Shadow @Final public ChunkMap chunkMap;
	@Shadow @Final public ServerLevel level;

	@Override
	@Unique
	public Thread dimThreads$getMainThread() {
		return this.mainThread;
	}

	@Override
	@Unique
	public void dimThreads$setMainThread(Thread thread) {
		this.mainThread = thread;
	}

	@Inject(method = "getTickingGenerated", at = @At("HEAD"), cancellable = true)
	private void getTotalChunksLoadedCount(CallbackInfoReturnable<Integer> ci) {
		if(!FMLLoader.isProduction()) {
			int count = this.chunkMap.getTickingGenerated();
			if(count < 441) ci.setReturnValue(441);
		}
	}

	@WrapOperation(method = "getChunk", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;currentThread()Ljava/lang/Thread;"))
	public Thread currentThread(Operation<Thread> original) {
		Thread thread = original.call();

		if(DimThread.MANAGER.isActive(this.level.getServer()) && DimThread.owns(thread)) {
			return this.mainThread;
		}

		return thread;
	}

}