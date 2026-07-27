package me.srrapero720.dimthread.mixin.impl;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.srrapero720.dimthread.DimThread;
import me.srrapero720.dimthread.thread.ChunkCache;
import me.srrapero720.dimthread.thread.IMutableMainThread;
import me.srrapero720.dimthread.util.ForeignChunkAccess;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.fml.loading.FMLLoader;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(value = ServerChunkCache.class, priority = 1001)
public abstract class ServerChunkCacheMixin extends ChunkSource implements IMutableMainThread {
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
			// A worker other than this chunk source's owner is about to drain an executor the owner may be
			// draining as well. Reported rather than fixed for now, see ForeignChunkAccess.
			if(thread != this.mainThread) ForeignChunkAccess.record(this.level.dimension(), thread, this.mainThread);

			return this.mainThread;
		}

		return thread;
	}

	/**
	 * {@link ServerChunkCacheMixin#currentThread} lets any worker take the main-thread branch of
	 * {@code getChunk}, which makes vanilla's lookup cache shared and readable torn. See {@link ChunkCache}.
	 */
	@Unique private final ThreadLocal<ChunkCache> dimthread$cache = ThreadLocal.withInitial(ChunkCache::new);

	/** Bumped by {@code clearCache()}, which cannot reach the caches owned by other threads. */
	@Unique private final AtomicInteger dimthread$cacheGeneration = new AtomicInteger();

	/** The per-thread cache, emptied first if another thread has invalidated it since we last looked. */
	@Unique
	private ChunkCache dimthread$localCache() {
		ChunkCache cache = this.dimthread$cache.get();
		int generation = this.dimthread$cacheGeneration.get();
		if(cache.isStale(generation)) cache.reset(generation);
		return cache;
	}

	/** Only the owning thread and our workers reach the cached branch; anyone else is dispatched async. */
	@Unique
	private boolean dimthread$usesCache() {
		Thread thread = Thread.currentThread();
		return thread == this.mainThread || DimThread.owns(thread);
	}

	/**
	 * Writes into this thread's cache and cancels the original, leaving vanilla's shared arrays empty so its
	 * own lookup loops always miss.
	 */
	@Inject(method = "storeInCache", at = @At("HEAD"), cancellable = true)
	private void dimthread$storeInLocalCache(long chunkPos, ChunkAccess chunk, ChunkStatus chunkStatus, CallbackInfo ci) {
		// Never let a FULL entry hold an imposter, or every later hit would fail Level#getChunk's cast.
		if(chunkStatus == ChunkStatus.FULL && chunk instanceof ImposterProtoChunk imposter) chunk = imposter.getWrapped();

		this.dimthread$localCache().store(chunkPos, chunk, chunkStatus);
		ci.cancel();
	}

	@Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
	private void dimthread$getChunkFromLocalCache(int x, int z, ChunkStatus chunkStatus, boolean requireChunk, CallbackInfoReturnable<ChunkAccess> cir) {
		if(!this.dimthread$usesCache()) return;

		int index = this.dimthread$localCache().find(ChunkPos.asLong(x, z), chunkStatus);
		if(index < 0) return;

		ChunkAccess chunk = this.dimthread$localCache().chunkAt(index);
		if(chunk != null || !requireChunk) cir.setReturnValue(chunk);
	}

	@Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
	private void dimthread$getChunkNowFromLocalCache(int chunkX, int chunkZ, CallbackInfoReturnable<LevelChunk> cir) {
		if(!this.dimthread$usesCache()) return;

		int index = this.dimthread$localCache().find(ChunkPos.asLong(chunkX, chunkZ), ChunkStatus.FULL);
		if(index < 0) return;

		// Matches vanilla: a cached non-LevelChunk answers "not loaded" rather than falling through.
		ChunkAccess chunk = this.dimthread$localCache().chunkAt(index);
		cir.setReturnValue(chunk instanceof LevelChunk levelChunk ? levelChunk : null);
	}

	@Inject(method = "clearCache", at = @At("HEAD"))
	private void dimthread$invalidateEveryThreadsCache(CallbackInfo ci) {
		// Any change makes every cache stale, so a lost concurrent increment is harmless.
		this.dimthread$cacheGeneration.incrementAndGet();
	}

	/**
	 * A worker can see the FULL future completed with an {@link ImposterProtoChunk} while the chunk is still
	 * being promoted, which breaks {@code Level#getChunk}'s cast. The imposter wraps exactly that chunk, so
	 * unwrapping returns the right one.
	 */
	@ModifyReturnValue(method = "getChunk", at = @At("RETURN"))
	private ChunkAccess dimthread$unwrapImposter(ChunkAccess original, int x, int z, ChunkStatus chunkStatus, boolean requireChunk) {
		if(chunkStatus == ChunkStatus.FULL && original instanceof ImposterProtoChunk imposter) return imposter.getWrapped();
		return original;
	}

}