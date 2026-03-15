package TSD.Plot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class SurvivalPlotPlugin extends JavaPlugin {

    // The name of the world used for plots
    private static final String PLOT_WORLD_NAME = "survivalplots";
    
    // Storage: PlotID -> OwnerUUID
    private Map<String, UUID> plotOwners = new HashMap<>();

    @Override
    public void onEnable() {
        // 1. Create data folder
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        // 2. ENSURE WORLD EXISTS
        // We use a custom generator to ensure it's a void world, avoiding biome errors
        if (!createWorld()) {
            getLogger().severe("CRITICAL: Could not create or load the plot world '" + PLOT_WORLD_NAME + "'. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Load plot data (simulated for this example)
        loadPlotData();

        // 4. Generate barriers and ID heads for existing plots on startup
        Bukkit.getScheduler().runTaskLater(this, this::generateWorldFeatures, 20L);
        
        getLogger().info("SurvivalPlotPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("SurvivalPlotPlugin has been disabled!");
    }

    /**
     * Checks if the world exists, and if not, creates it using a Void Generator.
     */
    private boolean createWorld() {
        if (Bukkit.getWorld(PLOT_WORLD_NAME) != null) {
            getLogger().info("World '" + PLOT_WORLD_NAME + "' already loaded.");
            return true;
        }

        getLogger().info("World '" + PLOT_WORLD_NAME + "' not found. Creating Void world now...");
        
        // Create a Void World
        WorldCreator creator = new WorldCreator(PLOT_WORLD_NAME);
        creator.type(WorldType.FLAT); // Use flat as base
        creator.generator(new VoidChunkGenerator()); // Use our custom void generator
        creator.generateStructures(false); // No structures needed for plots
        
        World world = creator.createWorld();
        
        if (world != null) {
            // Set the spawn point to a safe location (e.g., 0, 100, 0)
            Location spawn = new Location(world, 0, 100, 0);
            // Ensure the block at spawn is solid so players don't fall
            spawn.getBlock().getRelative(0, -1, 0).setType(Material.BEDROCK);
            world.setSpawnLocation(0, 100, 0);
            
            getLogger().info("Successfully created void world: " + PLOT_WORLD_NAME);
            return true;
        } else {
            getLogger().severe("Failed to create world: " + PLOT_WORLD_NAME);
            return false;
        }
    }

    /**
     * A simple Chunk Generator that creates a void world.
     * Uses the standard generateChunkData method which is compatible with 1.18+ versions.
     */
    public static class VoidChunkGenerator extends ChunkGenerator {
        
        
        @Override
        public ChunkData generateChunkData(World world, Random random, int x, int z, ChunkGenerator.BiomeGrid biome) {
            ChunkData chunk = createChunkData(world);
            
            // We leave the chunk empty (air) to create a void.
            // No blocks are set here, so it remains empty.
            
            return chunk;
        }
        
        // This ensures the world is treated as a void world in modern versions
        @Override
        public boolean isParallelCapable() {
            return true;
        }
    }

    private void loadPlotData() {
        // IMPLEMENTATION REQUIRED: 
        // Load your actual plot data here from config/database.
        // Example:
        // plotOwners.put("plot_1", UUID.fromString("some-uuid-here"));
    }

    private void generateWorldFeatures() {
        World world = Bukkit.getWorld(PLOT_WORLD_NAME);
        if (world == null) return;

        for (Map.Entry<String, UUID> entry : plotOwners.entrySet()) {
            String plotId = entry.getKey();
            UUID ownerUuid = entry.getValue();
            
            // 1. Identify Plot Location
            Location plotCenter = getPlotCenter(plotId);
            if (plotCenter == null) continue;

            // 2. Create Barrier Walls
            createBarrierBox(plotCenter);

            // 3. Create Identification Head
            // Explicitly use org.bukkit.OfflinePlayer to avoid conflict with TSD.Plot.OfflinePlayer
            org.bukkit.OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUuid);
            createIdentificationHead(plotCenter, owner);
        }
    }

    // --- Logic Implementation ---

    private Location getPlotCenter(String plotId) {
        // IMPLEMENTATION REQUIRED: 
        // You must replace this with your logic to determine where a plot is.
        // For this example, we parse "plot_X_Z" format.
        
        try {
            String[] parts = plotId.split("_");
            if (parts.length >= 3) {
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                // Use the specific plot world
                return new Location(Bukkit.getWorld(PLOT_WORLD_NAME), x, 100, z);
            }
        } catch (Exception e) {
            getLogger().warning("Could not parse location for plot ID: " + plotId);
        }
        
        return null; 
    }

    private void createBarrierBox(Location center) {
        if (center.getWorld() == null) return;

        int radius = 20; // Size of plot
        int height = 50; // Height of barrier wall
        
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        // Loop to create walls (North, South, East, West)
        for (int y = cy; y < cy + height; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                // North and South walls
                center.getWorld().getBlockAt(x, y, cz - radius).setType(Material.BARRIER);
                center.getWorld().getBlockAt(x, y, cz + radius).setType(Material.BARRIER);
            }
            for (int z = cz - radius; z <= cz + radius; z++) {
                // East and West walls
                center.getWorld().getBlockAt(cx - radius, y, z).setType(Material.BARRIER);
                center.getWorld().getBlockAt(cx + radius, y, z).setType(Material.BARRIER);
            }
        }
    }

    private void createIdentificationHead(Location loc, org.bukkit.OfflinePlayer owner) {
        // Place a skull at the center (1 block above ground)
        Location headLoc = loc.clone().add(0, 1, 0);
        Block block = headLoc.getBlock();
        
        block.setType(Material.PLAYER_HEAD);
        
        // Set the skull owner
        Skull skull = (Skull) block.getState();
        skull.setOwningPlayer(owner);
        skull.update();
    }

    // --- Command Handling ---

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("plotadmin")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }

            Player player = (Player) sender;

            if (!player.isOp()) {
                player.sendMessage("You do not have permission to use this plugin.");
                return true;
            }

            if (args.length == 0) {
                player.sendMessage("Usage: /plotadmin <plot_id>");
                return true;
            }

            String plotId = args[0];
            Location targetLoc = getPlotCenter(plotId);

            if (targetLoc == null) {
                player.sendMessage("Plot ID not found.");
                return true;
            }

            // Teleport the player safely (add 1 to Y to avoid suffocation in floor)
            Location teleportLoc = targetLoc.clone().add(0, 1, 0);
            player.teleport(teleportLoc);
            player.sendMessage("Teleported to plot: " + plotId);

            // LOGGING
            logAdminAction(player, plotId, teleportLoc);

            return true;
        }
        return false;
    }

    private void logAdminAction(Player admin, String plotId, Location loc) {
        File logFile = new File(getDataFolder(), "admin_teleports.log");
        
        try (FileWriter fw = new FileWriter(logFile, true);
             PrintWriter pw = new PrintWriter(fw)) {
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = sdf.format(new Date());
            
            pw.println("[" + timestamp + "] " + 
                       "Admin: " + admin.getName() + " (" + admin.getUniqueId() + ") " +
                       "teleported to PlotID: " + plotId + " " +
                       "at World: " + loc.getWorld().getName() + " " +
                       "X: " + loc.getBlockX() + " Y: " + loc.getBlockY() + " Z: " + loc.getBlockZ());
                       
        } catch (IOException e) {
            e.printStackTrace();
            getLogger().severe("Could not write to admin log file!");
        }
    }
}
