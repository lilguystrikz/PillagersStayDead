package com.strikz.pillagersstaydead;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents; // For death
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos; // Still used for StructureManager calls
import net.minecraft.core.registries.Registries;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.List; // Added for list of potential structures
import java.util.Iterator;
import java.util.Objects;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PillagersStayDead implements ModInitializer {
    public static final String MODID = "pillagersstaydead";
    private static final Logger LOGGER = LogUtils.getLogger();

    
    private static final Set<ResourceLocation> TRACKED_STRUCTURES;

    static {
        Set<ResourceLocation> structures = new HashSet<>();
        List<ResourceLocation> potentialStructures = List.of(
            ResourceLocation.fromNamespaceAndPath("minecraft", "pillager_outpost"),
            // Towns and Towers - Exclusives
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "exclusives/pillager_outpost_classic"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "exclusives/pillager_outpost_iberian"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "exclusives/pillager_outpost_mediterranean"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "exclusives/pillager_outpost_nilotic"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "exclusives/pillager_outpost_oriental"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "exclusives/pillager_outpost_rustic"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "exclusives/pillager_outpost_swedish"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "exclusives/pillager_outpost_tudor"),
            // Towns and Towers - Biome Variants
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_badlands"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_beach"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_birch_forest"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_desert"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_flower_forest"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_forest"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_grove"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_jungle"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_meadow"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_mushroom_fields"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_ocean"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_old_growth_taiga"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_savanna"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_savanna_plateau"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_snowy_beach"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_snowy_plains"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_snowy_slopes"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_snowy_taiga"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_sparse_jungle"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_sunflower_plains"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_swamp"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_taiga"),
            ResourceLocation.fromNamespaceAndPath("towns_and_towers", "pillager_outpost_wooded_badlands"),
            // Terralith - Biome Variants
            ResourceLocation.fromNamespaceAndPath("terralith", "desert_outpost"),
            // CTOV - Biome Varients
            ResourceLocation.fromNamespaceAndPath("ctov", "pillager_outpost_badlands"),
            ResourceLocation.fromNamespaceAndPath("ctov", "pillager_outpost_beach"),
            ResourceLocation.fromNamespaceAndPath("ctov", "pillager_outpost_dark_forest"),
            ResourceLocation.fromNamespaceAndPath("ctov", "pillager_outpost_desert"),
            ResourceLocation.fromNamespaceAndPath("ctov", "pillager_outpost_jungle"),
            ResourceLocation.fromNamespaceAndPath("ctov", "pillager_outpost_mesa"),
            ResourceLocation.fromNamespaceAndPath("ctov", "pillager_outpost_mountain"),
            ResourceLocation.fromNamespaceAndPath("ctov", "pillager_outpost_plains"),
            ResourceLocation.fromNamespaceAndPath("ctov", "pillager_outpost_savanna"),
            ResourceLocation.fromNamespaceAndPath("ctov", "pillager_outpost_snowy"),
            ResourceLocation.fromNamespaceAndPath("ctov", "pillager_outpost_swamp"),
            ResourceLocation.fromNamespaceAndPath("ctov", "pillager_outpost_taiga")
        );

        for (ResourceLocation loc : potentialStructures) {
            String namespace = loc.getNamespace();
            boolean shouldAdd = false;
            if (namespace.equals("minecraft")) {
                shouldAdd = true;
            } else if (namespace.equals("towns_and_towers")) {
                if (FabricLoader.getInstance().isModLoaded("t_and_t")) {
                    shouldAdd = true;
                } else {
                    LOGGER.info("PillagersStayDead: Mod 't_and_t' (Towns and Towers) not loaded, not tracking structure: {}", loc);
                }
            } else {
                // For other mods like terralith, ctov, assume namespace is mod ID
                if (FabricLoader.getInstance().isModLoaded(namespace)) {
                    shouldAdd = true;
                } else {
                    LOGGER.info("PillagersStayDead: Mod '{}' not loaded, not tracking structure: {}", namespace, loc);
                }
            }

            if (shouldAdd) {
                structures.add(loc);
            }
        }
        TRACKED_STRUCTURES = Set.copyOf(structures); // Make it immutable
        LOGGER.info("PillagersStayDead: Initialized with {} tracked structures.", TRACKED_STRUCTURES.size());
    }

    private final ConcurrentHashMap<BoundingBox, Set<UUID>> activeOutpostsPillagers = new ConcurrentHashMap<>();
    private final Set<BoundingBox> neutralizedOutposts = ConcurrentHashMap.newKeySet(); 
    private NeutralizedOutpostsData worldData;

    private final Map<Long, BoundingBox> outpostCache = new ConcurrentHashMap<>(); 
    private static final int SCAN_INTERVAL = 1200; // 60 seconds for player-centric scans
    private static final int ENTITY_CHECK_INTERVAL = 200; // 10 seconds for checking active outposts
    private static final int CHUNK_SCAN_PROCESSING_INTERVAL = 20; // Process pending chunks every 1 second
    private static final int MAX_PENDING_CHUNKS_TO_PROCESS_PER_INTERVAL = 10; // Max pending chunks to scan each interval
    private int tickCounter = 0; // Universal tick counter
    private final Set<ChunkPos> pendingChunkScans = ConcurrentHashMap.newKeySet();

    @Override
    public void onInitialize() {
        LOGGER.info("PillagersStayDead: {} mod setup", MODID);
        
        CommandRegistrationCallback.EVENT.register(this::onRegisterCommands);
        ServerChunkEvents.CHUNK_LOAD.register(this::onChunkLoad);
        ServerChunkEvents.CHUNK_UNLOAD.register(this::onChunkUnload);
        ServerWorldEvents.LOAD.register(this::onWorldLoad);
        ServerWorldEvents.UNLOAD.register(this::onWorldSave);
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(this::onEntityDeath);
        ServerTickEvents.END_SERVER_TICK.register(this::onWorldTick);
        ServerEntityEvents.ENTITY_LOAD.register(this::onEntityJoin);
    }

    public void onChunkLoad(ServerLevel serverLevel, ChunkAccess chunk) {
        if (serverLevel.dimension() != Level.OVERWORLD) return;
        
        if (!outpostCache.containsKey(chunk.getPos().toLong())) {
            pendingChunkScans.add(chunk.getPos());
        }
    }

    public void onChunkUnload(ServerLevel serverLevel, ChunkAccess chunk) {
        if (serverLevel.dimension() != Level.OVERWORLD) return;
        
        pendingChunkScans.remove(chunk.getPos());
    }

    public void onRegisterCommands(CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher, net.minecraft.commands.CommandBuildContext registryAccess, net.minecraft.commands.Commands.CommandSelection environment) {
        dispatcher.register(
            net.minecraft.commands.Commands.literal("psd_scan")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerLevel level = context.getSource().getLevel();
                    var player = context.getSource().getPlayer();
                    if (player == null) {
                        context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Command can only be run by a player."));
                        return 0;
                    }
                    
                    BlockPos pos = player.blockPosition();
                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("PillagersStayDead: Force scanning for outposts near " + pos), true);
                    
                    int scanRadius = Math.min(level.getServer().getPlayerList().getViewDistance(), 8); 
                    clearCacheInRadius(level, pos, scanRadius);
                    scanAndTrackExistingOutposts(level, pos, scanRadius);
                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("PillagersStayDead: Force scan complete. Active: " + activeOutpostsPillagers.size() + ", Neutralized: " + neutralizedOutposts.size() + ", Cached Chunks: " + outpostCache.size()), true);
                    return 1;
                })
        );
    }
    
    private void clearCacheInRadius(ServerLevel serverLevel, BlockPos centerPos, int radiusChunks) {
        int centerChunkX = centerPos.getX() >> 4;
        int centerChunkZ = centerPos.getZ() >> 4;
        int clearedCount = 0;
        for (int dX = -radiusChunks; dX <= radiusChunks; dX++) {
            for (int dZ = -radiusChunks; dZ <= radiusChunks; dZ++) {
                ChunkPos currentChunkPos = new ChunkPos(centerChunkX + dX, centerChunkZ + dZ);
                if (outpostCache.remove(currentChunkPos.toLong()) != null) {
                    clearedCount++;
                }
            }
        }
        if (clearedCount > 0) {
            LOGGER.debug("PillagersStayDead: Cleared {} chunk(s) from outpostCache for forced scan near {}.", clearedCount, centerPos);
        }
    }

    public void onWorldLoad(net.minecraft.server.MinecraftServer server, ServerLevel level) {    
        if (level.dimension() != Level.OVERWORLD) return;

        LOGGER.info("PillagersStayDead: Loading world data for dimension {}", level.dimension().location());
        DimensionDataStorage storage = level.getDataStorage();
        
        worldData = storage.computeIfAbsent(
            new SavedData.Factory<>(
                NeutralizedOutpostsData::new,
                (tag, provider) -> {
                    LOGGER.info("PillagersStayDead: Loading neutralized outposts data from NBT for dimension {}", level.dimension().location());
                    return NeutralizedOutpostsData.load(tag);
                },
                DataFixTypes.LEVEL
            ),
            NeutralizedOutpostsData.DATA_NAME
        );

        neutralizedOutposts.clear();
        if (worldData != null) {
            neutralizedOutposts.addAll(worldData.getNeutralizedOutposts());
            LOGGER.info("PillagersStayDead: Loaded {} neutralized outposts for dimension {}.", neutralizedOutposts.size(), level.dimension().location());
        } else {
            LOGGER.error("PillagersStayDead: Failed to load or create NeutralizedOutpostsData for dimension {}!", level.dimension().location());
        }
        
        LOGGER.info("PillagersStayDead: Skipping initial full world scan - structures will be detected as chunks load or via player proximity.");
    }

    public void onWorldSave(net.minecraft.server.MinecraftServer server, ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD || worldData == null) return;
            
        worldData.setNeutralizedOutposts(neutralizedOutposts);
        worldData.setDirty(); 
        LOGGER.info("PillagersStayDead: Saved {} neutralized outposts for dimension {}", neutralizedOutposts.size(), level.dimension().location());
    }

    public void onEntityDeath(ServerLevel level, net.minecraft.world.entity.Entity entity, net.minecraft.world.entity.LivingEntity killed) {
        if (level.isClientSide() || !(killed instanceof Pillager)) return;
        Pillager pillager = (Pillager) killed;
        
        ServerLevel serverLevel = (ServerLevel) level;
        if (serverLevel.dimension() != Level.OVERWORLD) return;

        UUID pillagerId = pillager.getUUID();
        BoundingBox outpostAssignedToPillager = null;
        for (Map.Entry<BoundingBox, Set<UUID>> entry : activeOutpostsPillagers.entrySet()) {
            if (entry.getValue().remove(pillagerId)) { 
                if (entry.getValue().isEmpty()) { 
                    outpostAssignedToPillager = entry.getKey();
                }
                break; 
            }
        }
        
        if (outpostAssignedToPillager != null) { 
            if (neutralizedOutposts.add(outpostAssignedToPillager)) {
                LOGGER.info("PillagersStayDead: Outpost at {} neutralized via pillager death.", outpostAssignedToPillager.getCenter());
            }
            if (worldData != null) {
                worldData.addNeutralizedOutpost(outpostAssignedToPillager); 
            }
            activeOutpostsPillagers.remove(outpostAssignedToPillager);
        }
    }

    public void onWorldTick(net.minecraft.server.MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        // Pass 'overworld' to methods that need ServerLevel
        tickCounter++;

        if (tickCounter % CHUNK_SCAN_PROCESSING_INTERVAL == 0 && !pendingChunkScans.isEmpty()) {
            Set<ChunkPos> chunksToActuallyScanThisInterval = new HashSet<>();
            Iterator<ChunkPos> iterator = pendingChunkScans.iterator();
            int processedThisInterval = 0;
            
            while(iterator.hasNext() && processedThisInterval < MAX_PENDING_CHUNKS_TO_PROCESS_PER_INTERVAL) {
                chunksToActuallyScanThisInterval.add(iterator.next());
                iterator.remove(); 
                processedThisInterval++;
            }

            if (!chunksToActuallyScanThisInterval.isEmpty()) {
                LOGGER.debug("PillagersStayDead: Processing {} pending chunks for outpost scan (radius 0).", chunksToActuallyScanThisInterval.size());
                for (ChunkPos chunkPos : chunksToActuallyScanThisInterval) {
                    scanAndTrackExistingOutposts(overworld, chunkPos.getMiddleBlockPosition(overworld.getMinBuildHeight() + overworld.getHeight() / 2), 0);
                }
            }
        }

        if (tickCounter % SCAN_INTERVAL == 0) {
            scanAndTrackExistingOutpostsAroundPlayers(overworld);
        }

        if (tickCounter % ENTITY_CHECK_INTERVAL == 0) {
            checkAndUpdateEntityTracking(overworld);
        }
    }

    private void checkAndUpdateEntityTracking(ServerLevel server) {
        if (activeOutpostsPillagers.isEmpty()) return;

        Set<BoundingBox> outpostsThatBecameEmpty = new HashSet<>();

        for (Map.Entry<BoundingBox, Set<UUID>> entry : activeOutpostsPillagers.entrySet()) {
            BoundingBox bb = entry.getKey();
            Set<UUID> trackedIds = entry.getValue(); 
            
            AABB aabb = new AABB(bb.minX(), bb.minY(), bb.minZ(), bb.maxX() + 1, bb.maxY() + 1, bb.maxZ() + 1);
            
            Set<UUID> currentPillagersInBB_UUIDs = new HashSet<>();
            server.getEntitiesOfClass(Pillager.class, aabb, p -> bb.isInside(p.blockPosition()))
                  .forEach(p -> currentPillagersInBB_UUIDs.add(p.getUUID()));

            if (currentPillagersInBB_UUIDs.isEmpty()) {
                outpostsThatBecameEmpty.add(bb);
            } else {
                trackedIds.retainAll(currentPillagersInBB_UUIDs); 
                trackedIds.addAll(currentPillagersInBB_UUIDs);   

                if (trackedIds.isEmpty()) { 
                    outpostsThatBecameEmpty.add(bb);
                }
            }
        }

        if (!outpostsThatBecameEmpty.isEmpty()) {
            for (BoundingBox bbToNeutralize : outpostsThatBecameEmpty) {
                if (neutralizedOutposts.add(bbToNeutralize)) {
                    LOGGER.info("PillagersStayDead: Outpost at {} neutralized via entity check.", bbToNeutralize.getCenter());
                }
                if (worldData != null) {
                    worldData.addNeutralizedOutpost(bbToNeutralize);
                }
                activeOutpostsPillagers.remove(bbToNeutralize);
            }
        }
    }

    public void onEntityJoin(net.minecraft.world.entity.Entity entity, ServerLevel level) { 
        if (level.isClientSide() || !(entity instanceof Pillager)) return;
        Pillager pillager = (Pillager) entity;

        ServerLevel serverLevel = level; // Use the passed level directly
        if (serverLevel.dimension() != Level.OVERWORLD) return;
        
        BlockPos spawnPos = pillager.blockPosition();
        ChunkPos spawnChunkPos = new ChunkPos(spawnPos);

        BoundingBox outpostBBFromCache = outpostCache.get(spawnChunkPos.toLong());
        if (outpostBBFromCache != null && outpostBBFromCache.isInside(spawnPos)) {
            if (neutralizedOutposts.contains(outpostBBFromCache)) {
                entity.discard(); // Correct way to remove entity
                LOGGER.debug("PillagersStayDead: Prevented Pillager spawn in neutralized outpost {} (cached in its chunk {})", outpostBBFromCache.getCenter(), spawnChunkPos);
                return;
            } else {
                activeOutpostsPillagers.computeIfAbsent(outpostBBFromCache, k -> ConcurrentHashMap.newKeySet()).add(pillager.getUUID());
                LOGGER.debug("PillagersStayDead: Tracked Pillager spawn in active outpost {} (cached in its chunk {})", outpostBBFromCache.getCenter(), spawnChunkPos);
                return;
            }
        }

        for (BoundingBox neutralizedBB : neutralizedOutposts) {
            if (neutralizedBB.isInside(spawnPos)) {
                entity.discard(); // Correct way to remove entity
                LOGGER.debug("PillagersStayDead: Prevented Pillager spawn in neutralized outpost {} (found via iteration)", neutralizedBB.getCenter());
                return;
            }
        }

        for (BoundingBox activeBB : activeOutpostsPillagers.keySet()) {
            if (activeBB.isInside(spawnPos)) {
                activeOutpostsPillagers.computeIfAbsent(activeBB, k -> ConcurrentHashMap.newKeySet()).add(pillager.getUUID());
                LOGGER.debug("PillagersStayDead: Tracked Pillager spawn in active outpost {} (found via iteration)", activeBB.getCenter());
                return;
            }
        }
    }

    private void scanAndTrackExistingOutpostsAroundPlayers(ServerLevel serverLevel) {
        int viewDistanceChunks = Math.min(serverLevel.getServer().getPlayerList().getViewDistance(), 8); 
        
        if (serverLevel.players().isEmpty()) {
            return;
        }

        serverLevel.players().forEach(player -> {
            scanAndTrackExistingOutposts(serverLevel, player.blockPosition(), viewDistanceChunks);
        });
    }
    
    // Scans a square area of (2*radiusChunks+1)x(2*radiusChunks+1) chunks around centerPos
    private void scanAndTrackExistingOutposts(ServerLevel serverLevel, BlockPos centerPos, int radiusChunks) {
        Set<ChunkPos> chunksToScanForNewOutposts = new HashSet<>();
        int centerChunkX = centerPos.getX() >> 4;
        int centerChunkZ = centerPos.getZ() >> 4;
        
        for (int dX = -radiusChunks; dX <= radiusChunks; dX++) {
            for (int dZ = -radiusChunks; dZ <= radiusChunks; dZ++) {
                ChunkPos currentChunkPos = new ChunkPos(centerChunkX + dX, centerChunkZ + dZ);
                if (!outpostCache.containsKey(currentChunkPos.toLong())) { 
                    chunksToScanForNewOutposts.add(currentChunkPos);
                }
            }
        }

        if (chunksToScanForNewOutposts.isEmpty()) {
            return;
        }

        LOGGER.debug("PillagersStayDead: Scanning {} new chunks for outposts near {} (radius {} chunks)", chunksToScanForNewOutposts.size(), centerPos, radiusChunks);
        
        var structureRegistry = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Set<BoundingBox> processedOutpostsThisScan = new HashSet<>(); 

        for (ChunkPos chunkPosToScan : chunksToScanForNewOutposts) {
            int chunkX = chunkPosToScan.x;
            int chunkZ = chunkPosToScan.z;
            
            ChunkAccess refChunk = serverLevel.getChunk(chunkX, chunkZ, ChunkStatus.STRUCTURE_REFERENCES, false); 
            if (refChunk == null) { 
                if(radiusChunks > 0) { // For player-centric scans (larger radius), if chunk isn't loaded, just skip it.
                    continue; 
                }
                // For onChunkLoad scans (radius 0), log if chunk not found, as it was expected.
                LOGGER.debug("PillagersStayDead: Chunk ({},{}) expected from onChunkLoad was not found or empty for STRUCTURE_REFERENCES scan.", chunkX, chunkZ);
                continue;
            }

            ChunkAccess chunkForStructureCheck = serverLevel.getChunk(chunkX, chunkZ, ChunkStatus.FEATURES, true); 
            if (chunkForStructureCheck == null) {
                LOGGER.warn("PillagersStayDead: Could not load chunk ({},{}) to FEATURES status. Skipping for this scan iteration.", chunkX, chunkZ);
                continue; 
            }

            for (ResourceLocation structureId : TRACKED_STRUCTURES) {
                Structure structure = structureRegistry.get(structureId);
                if (structure == null) {
                    LOGGER.warn("PillagersStayDead: Unknown structure ID in TRACKED_STRUCTURES: {}", structureId);
                    continue;
                }
                
                StructureStart start = null;
                // Iterate through sections of the chunk to find the structure start
                // Using chunkForStructureCheck which is at least FEATURES status
                for (int secY = chunkForStructureCheck.getMinSection(); secY < chunkForStructureCheck.getMaxSection(); ++secY) {
                    SectionPos currentSectionPos = SectionPos.of(chunkPosToScan.x, secY, chunkPosToScan.z);
                    // Use structureManager().getStartForStructure with the chunk at FEATURES status
                    start = serverLevel.structureManager().getStartForStructure(currentSectionPos, structure, chunkForStructureCheck);
                    if (start != null && start.isValid()) break; 
                }
                    
                if (start != null && start.isValid() && !start.getPieces().isEmpty()) {
                    BoundingBox bb = start.getBoundingBox();
                    
                    if (processedOutpostsThisScan.contains(bb)) { 
                        continue; // Already processed this specific outpost BB in this scan call
                    }
                    processedOutpostsThisScan.add(bb);

                    // Log identified structure type
                    String structureTypeNamespace = structureId.getNamespace();
                    if (structureTypeNamespace.equals("minecraft") || structureTypeNamespace.equals("towns_and_towers") || structureTypeNamespace.equals("terralith") || structureTypeNamespace.equals("ctov")) {
                        LOGGER.info("PillagersStayDead: Found {} outpost: {} at {}", structureTypeNamespace, structureId.getPath(), bb.getCenter());
                    }


                    // Populate outpost cache for all chunks this BB occupies
                    int minOutpostChunkX = bb.minX() >> 4;
                    int maxOutpostChunkX = bb.maxX() >> 4;
                    int minOutpostChunkZ = bb.minZ() >> 4;
                    int maxOutpostChunkZ = bb.maxZ() >> 4;
                    
                    for (int ox = minOutpostChunkX; ox <= maxOutpostChunkX; ox++) {
                        for (int oz = minOutpostChunkZ; oz <= maxOutpostChunkZ; oz++) {
                            long outpostChunkPartKey = new ChunkPos(ox, oz).toLong();
                            BoundingBox oldBB = outpostCache.putIfAbsent(outpostChunkPartKey, bb); 
                            if (oldBB != null && !oldBB.equals(bb)) {
                                LOGGER.warn("PillagersStayDead: Chunk ({},{}) was already in outpostCache for structure outpost with different BB. Old: {}, New: {}. Kept old mapping to: {}.", ox, oz, oldBB, bb, oldBB);
                            }
                        }
                    }

                    // --- START OF CORRECTED LOGIC FOR HANDLING NEWLY FOUND OUTPOSTS ---
                    if (neutralizedOutposts.contains(bb)) {
                        LOGGER.debug("PillagersStayDead: Structure outpost at {} is already neutralized (found during scan).", bb.getCenter());
                    } else {
                        // Outpost is not currently neutralized.
                        // Check for any pillagers currently physically present.
                        AABB expandedBB_forPillagerCheck = new AABB(bb.minX(), bb.minY(), bb.minZ(), bb.maxX() + 1, bb.maxY() + 1, bb.maxZ() + 1);
                        Set<UUID> currentPillagersInBB_UUIDs = new HashSet<>();
                        serverLevel.getEntitiesOfClass(Pillager.class, expandedBB_forPillagerCheck, p -> bb.isInside(p.blockPosition()))
                            .forEach(p -> currentPillagersInBB_UUIDs.add(p.getUUID()));

                        boolean isCurrentlyInActiveMap = activeOutpostsPillagers.containsKey(bb);

                        if (!currentPillagersInBB_UUIDs.isEmpty()) {
                            // Pillagers are present.
                            if (!isCurrentlyInActiveMap) {
                                // New outpost, found with pillagers. Add to active map.
                                activeOutpostsPillagers.computeIfAbsent(bb, k -> {
                                    LOGGER.info("PillagersStayDead: New outpost at {} found with {} pillager(s). Adding to active tracking.", bb.getCenter(), currentPillagersInBB_UUIDs.size());
                                    return ConcurrentHashMap.newKeySet();
                                }).addAll(currentPillagersInBB_UUIDs);
                            } else {
                                // Already active, and still has pillagers. Good.
                                // The checkAndUpdateEntityTracking method will handle precise UUID syncing if needed.
                                LOGGER.trace("PillagersStayDead: Rescanned active outpost at {}. {} pillager(s) present.", bb.getCenter(), currentPillagersInBB_UUIDs.size());
                            }
                        } else {
                            // No pillagers currently physically in this outpost.
                            if (isCurrentlyInActiveMap) {
                                // Was active, but this scan found it empty.
                                // checkAndUpdateEntityTracking is responsible for neutralization if it remains empty.
                                LOGGER.debug("PillagersStayDead: Scan found active outpost at {} to be empty. Periodic check will confirm status.", bb.getCenter());
                            } else {
                                // New (not previously active) outpost, and it's currently empty.
                                // Do NOT add to activeOutpostsPillagers. It is in outpostCache.
                                // It will become active if pillagers spawn in it later (handled by onEntityJoin).
                                LOGGER.info("PillagersStayDead: New outpost at {} found empty. Cached. Will become active if pillagers spawn.", bb.getCenter());
                            }
                        }
                    }
                    // --- END OF CORRECTED LOGIC ---
                    break; // Found a tracked structure type in this chunk, process it and move to next chunk.
                }
            }
        } 
    }

    public static class NeutralizedOutpostsData extends SavedData {
        public static final String DATA_NAME = PillagersStayDead.MODID + "_neutralized_outposts_overworld"; 
        private final Set<BoundingBox> neutralizedSet = new HashSet<>();

        public NeutralizedOutpostsData() {}
        
        public static NeutralizedOutpostsData load(CompoundTag tag) {
            NeutralizedOutpostsData data = new NeutralizedOutpostsData();
            LOGGER.info("PillagersStayDead: Loading NeutralizedOutpostsData from NBT...");
            if (tag.contains("NeutralizedPillagerOutposts", Tag.TAG_LIST)) {
                ListTag list = tag.getList("NeutralizedPillagerOutposts", Tag.TAG_COMPOUND);
                for (Tag t : list) {
                    if (t instanceof CompoundTag bbTag) {
                        BoundingBox bb = new BoundingBox(
                            bbTag.getInt("minX"), bbTag.getInt("minY"), bbTag.getInt("minZ"),
                            bbTag.getInt("maxX"), bbTag.getInt("maxY"), bbTag.getInt("maxZ")
                        );
                        data.neutralizedSet.add(bb);
                    }
                }
                LOGGER.info("PillagersStayDead: Successfully loaded {} neutralized outposts from NBT.", data.neutralizedSet.size());
            } else {
                LOGGER.info("PillagersStayDead: No 'NeutralizedPillagerOutposts' list found in NBT for NeutralizedOutpostsData.");
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            LOGGER.info("PillagersStayDead: Saving {} neutralized outposts to NBT...", neutralizedSet.size());
            ListTag list = new ListTag();
            for (BoundingBox bb : neutralizedSet) {
                CompoundTag bbTag = new CompoundTag();
                bbTag.putInt("minX", bb.minX());
                bbTag.putInt("minY", bb.minY());
                bbTag.putInt("minZ", bb.minZ());
                bbTag.putInt("maxX", bb.maxX());
                bbTag.putInt("maxY", bb.maxY());
                bbTag.putInt("maxZ", bb.maxZ());
                list.add(bbTag);
            }
            tag.put("NeutralizedPillagerOutposts", list);
            return tag;
        }

        public Set<BoundingBox> getNeutralizedOutposts() {
            return new HashSet<>(neutralizedSet); 
        }

        public void setNeutralizedOutposts(Set<BoundingBox> outposts) {
            this.neutralizedSet.clear();
            this.neutralizedSet.addAll(outposts);
            setDirty();
            LOGGER.info("PillagersStayDead: Neutralized outposts set in SavedData updated ({} entries), marked dirty.", this.neutralizedSet.size());
        }

        public void addNeutralizedOutpost(BoundingBox bb) {
            if (this.neutralizedSet.add(bb)) { 
                setDirty();
                LOGGER.info("PillagersStayDead: Added new neutralized outpost BB: {} to SavedData. Marked dirty.", bb.getCenter());
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NeutralizedOutpostsData that = (NeutralizedOutpostsData) o;
            return Objects.equals(neutralizedSet, that.neutralizedSet);
        }

        @Override
        public int hashCode() {
            return Objects.hash(neutralizedSet);
        }
    }
}
