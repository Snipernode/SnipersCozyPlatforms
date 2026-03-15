package TSD.Plot;

import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.lang.reflect.Method;
import java.util.Random;

public class VoidGenerator extends ChunkGenerator {

    // Default biome to use if not specified in config
    private final String defaultBiome;

    public VoidGenerator() {
        this.defaultBiome = "plains";
    }

    @Override
    public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biomeGrid) {
        // Create a completely empty chunk data object
        ChunkData chunkData = createChunkData(world);

        // Apply Biomes
        String biomeName = PlatformsPlugin.getInstance().getConfig().getString("world.biome", defaultBiome);
        Biome biomeToSet;
        try {
            biomeToSet = Biome.valueOf(biomeName.toUpperCase().replace("Minecraft:", ""));
        } catch (IllegalArgumentException | NullPointerException e) {
            biomeToSet = Biome.PLAINS;
        }

        // Try to use the modern setBlockBiome method (Available in 1.16.4+)
        // This is generally safer than BiomeGrid reflection for 3D biomes
        try {
            // Check if setBlockBiome exists via reflection to be safe
            Method setBlockBiome = chunkData.getClass().getMethod("setBlockBiome", int.class, int.class, int.class, Biome.class);
            
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();

            // Iterate through the chunk to set the biome for every block column
            // Note: In modern versions, biomes are stored in 4x4x4 volumes, so setting every 4th Y is sufficient.
            for (int cx = 0; cx < 16; cx++) {
                for (int cz = 0; cz < 16; cz++) {
                    for (int cy = minY; cy < maxY; cy += 4) {
                        setBlockBiome.invoke(chunkData, cx, cy, cz, biomeToSet);
                    }
                }
            }

        } catch (NoSuchMethodException e) {
            // Fallback for older versions (pre-1.16.4): Use BiomeGrid reflection
            applyLegacyBiomes(biomeGrid, biomeToSet);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return chunkData;
    }

    private void applyLegacyBiomes(BiomeGrid biomeGrid, Biome biomeToSet) {
        try {
            // Try 3D BiomeGrid method (1.16 - 1.17.1)
            Method setBiome3D = BiomeGrid.class.getMethod("setBiome", int.class, int.class, int.class, Biome.class);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 256; y += 4) { // Legacy height was usually 0-256
                        setBiome3D.invoke(biomeGrid, x, y, z, biomeToSet);
                    }
                }
            }
        } catch (NoSuchMethodException e1) {
            try {
                // Fallback to 2D BiomeGrid method (1.15 and older)
                Method setBiome2D = BiomeGrid.class.getMethod("setBiome", int.class, int.class, Biome.class);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        setBiome2D.invoke(biomeGrid, x, z, biomeToSet);
                    }
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
    
    @Override
    public boolean shouldGenerateMobs() {
        return false; 
    }

    @Override
    public void generateBedrock(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // Do nothing.
    }
}
