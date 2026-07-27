# 🌌 Dimensional Threading ReForked
Optimises dimension processing assigning them independent threads.
Having a lot of dimensions on multi-core CPU will increase performance significantly.
Works on singleplayer and multiplayer, can be installed on server-side only,
but having it also on client-side is always a plusplus.


This is the unofficial for of DimensionalThreading-Reforged by ccr4ft3r
which is also an unofficial port of DimensionalThreading by WearBlackAllDay
which is also an defivated mod of another dimension mod by 2No2Name but i can't find it.
Also contains the WorldThreaded patches by 2No2Name, making this project the fork²

## 🛠️ Changes among ports
- Fixed [#17](https://github.com/CCr4ft3r/DimensionalThreading-Reforged/issues/17)
- Support for 1.16.5 ~ 1.21.1 for Forge, NeoForge and Fabric
- Rewrite of latch and crash report handling
- Fixed Cupboard incompatibility (log spam + entities don't spawn)

Mod wants to be vanilla-like, making it compatible with Carpet Mod, Sodium/Embeddium,
ImmersivePortals and many other mods.

# 📖 Quick Wiki
**DimThread comes with 2 new Gamerules:**
- ``/gamerule dimthread_active true/false``; enables/disables the mod
- ``/gamerule dimthread_thread_count <count>``; changes the amount of threads used
- ``/gamerule dimthread_skip_crashing true/false``; skip crashes on dimensions

**Know incompatibilities**
- ``AppliedEnergistics2``: dimensional features doesn't works (no crashes). Grids spanning dimensions stay unsafe, block entities in different dimensions touch one grid while both levels tick
- Mods that only guarded with ``isSameThread()`` no longer crash, but their collections are now reachable from several dimension threads. ``ModernIndustrialization`` multiblock tracking is one case

## 📱 Contact and Support
You can contact me on my Discord Server or the mod's Github Repository for support...
or if you want to talk about the life.
---
# 🎮 Squash the mod power - Rent a multi-core server
[![](https://i.imgur.com/2WFmJzc.png)](https://www.kinetichosting.net/game-servers)
---

# ❓Frequent Answers and Questions
### Mod change Vanilla behaviour?
- DimThreads is aimed to keep the vanilla-like behavior with no much derivations. If you find any difference from vanilla (excepting better performance) open an issue.
### Dimensions can get de-synchronized?
- Nope. DimThread will always synchronize the dimensions it threads with each other, setting the overall MSPT to the slowest individual dimension.

### How many dimensions are supported?
- Yes, but you will have to adjust the GameRule accordingly. Or the config file to override default values

### Server will run faster assigning more threads than dimensions on my server?
- No, the mod can only assign one dimension to one thread.

### Can i use DimThread if i have less threads on my CPU than dimensions?
- The mod will not crash if you do not have enough threads available, but it will make the game slower. You should always have at leastDimensionCount + 1threads available.

### How is compatibility with other mods?
- Compatibility for very well-know mods is always ensured like Lithium/Radiun/Canary or ModernFix. Since not every author writes their mod thread safe, some mods will experiment minor issues on their features. This includes: AE2, Bigger Reactors, Chunky Pregenerator, and few others

### What happens if the thread counts exceeds CPU thread counts
- Nothing, maybe a little-bit slow performance but nothing to worry about

### Why spark show a high usage from DimThreads
- Spark is a developer-tool mod and should be threated as-is, Dimthreads overrides basic Minecraft behavior to replicate it on other threads besides "Server Thread". we delegate all the tick work to Dimthreads threeads, and make server thread sit down and wait until all threads ends

---