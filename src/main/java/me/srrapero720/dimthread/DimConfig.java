package me.srrapero720.dimthread;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.*;

public class DimConfig {
    private static final ModConfigSpec SPEC;

    public static final IntValue DEFAULT_GAMERULE_THREADS;
    public static final BooleanValue IGNORE_TICK_CRASH;

    static {
        Builder B = new Builder();

        DEFAULT_GAMERULE_THREADS = B
                .comment(
                        "Define the initial thread count number of threads",
                        "If the value is 6, new worlds will start with 6 thread counts as a initial value",
                        "This is useful for modpacks with dimensional mods, instead of fallback on default's 3 you can configure how many can use",
                        "ADVICE: Gamerule and this config is capped to max available processors of the server",
                        "if you set the value above available processors, forge will set it back to defaults 3",
                        "This was done to prevent users gameplay got slowed by not having enough threads"
                )
                .defineInRange("default_gamerule_threads", 3, 2, Runtime.getRuntime().availableProcessors());

        IGNORE_TICK_CRASH = B
                .comment(
                        "WARNING: very VERY EXPERIMENTAL, do not use it (except if you want world corruption)",
                        "This feature is intended to ignore crashes ticking levels, for the good sake of not have to restart your entire server",
                        "Have i mentioned IS EXPERIMENTAL?"
                ).define("ignore_tick_crash", false);

        SPEC = B.build();
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}
