package com.strikz.pillagersstaydead;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration class for the PillagersStayDead mod.
 */
public class Config {
    public static final ModConfigSpec SPEC;
    
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        
        // Add configuration options here in the future
        
        SPEC = builder.build();
    }
    
    /**
     * Called when the config needs to be reloaded
     */
    public static void loadConfig() {
        // Load configuration values here in the future
    }
}
