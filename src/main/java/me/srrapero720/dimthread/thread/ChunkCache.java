package me.srrapero720.dimthread.thread;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Arrays;

/**
 * Thread-confined copy of {@code ServerChunkCache}'s four-entry lookup cache.
 * <p>
 * Vanilla holds that cache in three parallel arrays and updates them with a non-atomic shift, which is only
 * safe because the server thread is its sole user. Our workers reach the same branch of {@code getChunk}, so a
 * reader could match the position and status of one entry while reading the chunk of another, returning a
 * chunk for the wrong status or position. Per-thread copies remove the sharing without taking a lock on what
 * is the hottest path in the game.
 * <p>
 * {@code clearCache()} cannot reach the copies owned by other threads, so invalidation goes through a
 * generation counter instead.
 */
public final class ChunkCache {
    private static final int SIZE = 4;

    private final long[] pos = new long[SIZE];
    private final ChunkStatus[] status = new ChunkStatus[SIZE];
    private final ChunkAccess[] chunk = new ChunkAccess[SIZE];
    private int generation;

    public ChunkCache() {
        this.reset(Integer.MIN_VALUE);
    }

    public boolean isStale(int generation) {
        return this.generation != generation;
    }

    /** Drops every entry and adopts {@code generation}, releasing the chunk references held until now. */
    public void reset(int generation) {
        Arrays.fill(this.pos, ChunkPos.INVALID_CHUNK_POS);
        Arrays.fill(this.status, null);
        Arrays.fill(this.chunk, null);
        this.generation = generation;
    }

    /** Index of the entry for {@code chunkPos} at {@code chunkStatus}, or {@code -1} when absent. */
    public int find(long chunkPos, ChunkStatus chunkStatus) {
        for (int i = 0; i < SIZE; i++) {
            if (this.pos[i] == chunkPos && this.status[i] == chunkStatus) return i;
        }
        return -1;
    }

    /** May legitimately be {@code null}: vanilla caches misses too, for {@code requireChunk == false}. */
    public ChunkAccess chunkAt(int index) {
        return this.chunk[index];
    }

    public void store(long chunkPos, ChunkAccess chunkAccess, ChunkStatus chunkStatus) {
        for (int i = SIZE - 1; i > 0; i--) {
            this.pos[i] = this.pos[i - 1];
            this.status[i] = this.status[i - 1];
            this.chunk[i] = this.chunk[i - 1];
        }

        this.pos[0] = chunkPos;
        this.status[0] = chunkStatus;
        this.chunk[0] = chunkAccess;
    }
}
