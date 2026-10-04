package myau.util;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockESPCache {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private final List<String> blocks;

    private final Map<Long, Set<BlockPos>> byChunk = new ConcurrentHashMap<>();
    private final Deque<long[]> scanQueue = new ArrayDeque<>();

    private Set<String> matcherIds;
    private Map<String, Object> matcherWildcards;
    private int lastListHash;

    public BlockESPCache(List<String> blocks) {
        this.blocks = blocks;
        rebuildMatcher();
    }

    public void clear() {
        this.byChunk.clear();
        this.scanQueue.clear();
    }

    public void enqueueChunk(int chunkX, int chunkZ) {
        if (this.blocks.isEmpty()) {
            return;
        }
        this.scanQueue.addLast(new long[]{chunkX, chunkZ});
    }

    public void removeChunk(int chunkX, int chunkZ) {
        this.byChunk.remove(key(chunkX, chunkZ));
    }

    public void enqueueLoadedChunks() {
        if (this.blocks.isEmpty()) {
            return;
        }
        this.scanQueue.clear();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        int renderDistance = mc.gameSettings.renderDistanceChunks;
        int playerChunkX = (int) mc.thePlayer.posX >> 4;
        int playerChunkZ = (int) mc.thePlayer.posZ >> 4;
        for (int cx = playerChunkX - renderDistance; cx <= playerChunkX + renderDistance; cx++) {
            for (int cz = playerChunkZ - renderDistance; cz <= playerChunkZ + renderDistance; cz++) {
                Chunk chunk = mc.theWorld.getChunkFromChunkCoords(cx, cz);
                if (chunk != null && !(chunk instanceof EmptyChunk)) {
                    enqueueChunk(cx, cz);
                }
            }
        }
    }

    public void tickScan(int maxSections) {
        if (mc.theWorld == null) {
            return;
        }
        refreshMatcher();
        if (this.matcherIds.isEmpty() && this.matcherWildcards.isEmpty()) {
            return;
        }
        int remaining = maxSections;
        while (remaining > 0 && !this.scanQueue.isEmpty()) {
            long[] chunkPos = this.scanQueue.pollFirst();
            int cx = (int) chunkPos[0];
            int cz = (int) chunkPos[1];
            Chunk chunk = mc.theWorld.getChunkFromChunkCoords(cx, cz);
            if (chunk == null || chunk instanceof EmptyChunk) {
                continue;
            }
            remaining -= scanChunk(chunk);
        }
    }

    public void onBlockChange(BlockPos pos, IBlockState newState) {
        refreshMatcher();
        long chunkKey = key(pos.getX() >> 4, pos.getZ() >> 4);
        if (matches(newState)) {
            this.byChunk.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
        } else {
            Set<BlockPos> set = this.byChunk.get(chunkKey);
            if (set != null) {
                set.remove(pos);
            }
        }
    }

    public void onSettingsChanged() {
        rebuildMatcher();
        rescanAll();
    }

    public void rescanAll() {
        this.byChunk.clear();
        this.scanQueue.clear();
        if (this.blocks.isEmpty()) {
            return;
        }
        enqueueLoadedChunks();
    }

    public Iterable<Map.Entry<Long, Set<BlockPos>>> entries() {
        return this.byChunk.entrySet();
    }

    public int totalCached() {
        int total = 0;
        for (Set<BlockPos> set : this.byChunk.values()) {
            total += set.size();
        }
        return total;
    }

    public boolean hasPendingScans() {
        return !this.scanQueue.isEmpty();
    }

    public boolean contains(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        long chunkKey = key(pos.getX() >> 4, pos.getZ() >> 4);
        Set<BlockPos> set = this.byChunk.get(chunkKey);
        return set != null && set.contains(pos);
    }

    private int scanChunk(Chunk chunk) {
        int scanned = 0;
        long chunkKey = key(chunk.xPosition, chunk.zPosition);
        Set<BlockPos> found = ConcurrentHashMap.newKeySet();
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        int baseX = chunk.xPosition << 4;
        int baseZ = chunk.zPosition << 4;
        for (int si = 0; si < sections.length; si++) {
            ExtendedBlockStorage section = sections[si];
            if (section == null) {
                continue;
            }
            scanned++;
            int baseY = si << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        IBlockState state = section.get(x, y, z);
                        if (matches(state)) {
                            found.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
                        }
                    }
                }
            }
        }
        if (!found.isEmpty()) {
            this.byChunk.put(chunkKey, found);
        } else {
            this.byChunk.remove(chunkKey);
        }
        return Math.max(scanned, 1);
    }

    private boolean matches(IBlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        if (block == null) {
            return false;
        }
        Object nameObj = Block.blockRegistry.getNameForObject(block);
        if (nameObj == null) {
            return false;
        }
        String registryId = nameObj.toString();
        if (this.matcherWildcards.containsKey(registryId)) {
            return true;
        }
        int meta = block.getMetaFromState(state);
        if (meta != 0 && this.matcherIds.contains(registryId + ":" + meta)) {
            return true;
        }
        return this.matcherIds.contains(registryId);
    }

    private void refreshMatcher() {
        int hash = this.blocks.hashCode();
        if (hash != this.lastListHash) {
            rebuildMatcher();
        }
    }

    private void rebuildMatcher() {
        this.lastListHash = this.blocks.hashCode();
        this.matcherIds = new HashSet<>();
        this.matcherWildcards = new HashMap<>();
        for (String id : this.blocks) {
            if (id.endsWith(":*")) {
                this.matcherWildcards.put(id.substring(0, id.length() - 2), null);
            } else {
                this.matcherIds.add(id);
            }
        }
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static List<String> parseBlockList(String text) {
        List<String> blocks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return blocks;
        }
        for (String entry : text.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty() && !blocks.contains(trimmed)) {
                blocks.add(trimmed);
            }
        }
        return blocks;
    }
}
