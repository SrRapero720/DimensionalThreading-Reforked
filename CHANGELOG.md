# UPDATE 1.2.2
- 🚀 Dimensions now really tick in parallel
  - 🛠️ `swapThreadsAndRun` was `static synchronized`, so a whole level tick ran holding one global monitor
  - ⚠️ First release where dimensions run at the same time, raise `/gamerule dimthread_thread_count` to benefit
- 🐛 Fixed crash ticking chunks (`ImposterProtoChunk cannot be cast to LevelChunk`)
  - 🛠️ `ServerChunkCache`'s lookup cache is per thread now, it was shared between workers and could be read torn
- 🐛 Fixed `ConcurrentModificationException` with AE2/AppFlux (`Ticking grid on end of level tick`)
  - 🛠️ `LevelTickEvent` dispatch is serialized again, the level tick between both events still runs in parallel
- 🐛 Fixed crashes with mods asserting they run on the server thread, like ModernIndustrialization
  - 🛠️ `MinecraftServer#isSameThread` accepts dimthread workers, scheduling still defers to the server thread
- 🐛 Fixed `/neoforge tps` showing every dimension as 20 TPS / 0.00ms
  - 🛠️ Per-level tick times are recorded again, vanilla records them inside the loop we replace
- 🛠️ Exceptions thrown by mods inside `LevelTickEvent` are reported instead of killing the worker silently
- 🛠️ Thread owners are restored in a `finally`, a throwing tick event could leave a level pinned to a worker
- 🛠️ Warn when a worker drives a chunk source it does not own
- 🛠️ Pinned NeoGradle and the publishing plugins, `7.+` resolved to a release needing a newer Gradle
- 🛠️ Made `gradlew` executable

# UPDATE 1.2.1
- 🐛 Fixed incompatibility with Tardis Refined
  - 🛠️ Get tiles now skips thread check on levels when both threads (caller and level) are dimthreads   
- 🛠️ Added mixin tech to be able to set `synchronized` and `volatile` on methods and fields
- 🛠️ Removed spawncapcontrol incompatibility (not confirmed)