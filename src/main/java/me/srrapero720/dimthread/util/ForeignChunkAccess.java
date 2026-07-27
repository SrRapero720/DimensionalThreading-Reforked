package me.srrapero720.dimthread.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static me.srrapero720.dimthread.DimThread.LOGGER;

/**
 * Warns when a worker drives a chunk source it does not own, which lets two threads run one dimension's chunk
 * tasks at the same time.
 * <p>
 * Confining {@code getChunk} to the owning thread would deadlock instead: the work lands on an executor that
 * nobody drains once that dimension has finished ticking. A real fix needs the barrier to keep exactly one
 * drainer available per executor, so for now this only reports the hazard.
 */
public final class ForeignChunkAccess {
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
    private static final AtomicLong COUNT = new AtomicLong();
    private static final long REPORT_EVERY = 10_000L;

    private ForeignChunkAccess() {}

    public static void record(ResourceKey<Level> owner, Thread caller, Thread ownerThread) {
        long total = COUNT.incrementAndGet();

        if (SEEN.add(caller.getName() + " -> " + owner.location())) {
            LOGGER.warn("'{}' is driving the chunk source of '{}' (owned by '{}'). Both can run that dimension's chunk tasks at once. Occurrence #{}.",
                    caller.getName(), owner.location(), ownerThread.getName(), total);
        }

        if (total % REPORT_EVERY == 0) {
            LOGGER.warn("Cross-dimension chunk access has now happened {} times across {} thread/dimension pairs.", total, SEEN.size());
        }
    }
}
