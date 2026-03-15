package TSD.Plot.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import TSD.Plot.PlatformsPlugin;

public class BlockInteractListener implements Listener {

    private final PlatformsPlugin plugin;

    public BlockInteractListener(PlatformsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Check if the player is in the Skyblock/Plot world
        if (plugin.getSkyblockWorld() != null && player.getWorld().equals(plugin.getSkyblockWorld())) {
            // We check for the bed setup asynchronously or after a tick to ensure the chunk is loaded
            Bukkit.getScheduler().runTaskLater(plugin, () -> setupPlotStructure(player), 20L); // 1 second delay
        }
    }

    private void setupPlotStructure(Player player) {
        Location loc = player.getLocation();
        Block blockAtFeet = loc.getBlock();
        Block blockBelow = blockAtFeet.getRelative(0, -1, 0);

        // Check if the special bed already exists nearby
        if (isProtectedBed(blockAtFeet) || isProtectedBed(blockBelow)) {
            return; // Bed exists, do nothing
        }

        // If we are here, the bed is missing. Let's place it.
        // We will place the bed at the player's feet.
        // Note: Ensure the area is clear (air) to avoid glitching blocks.
        
        // 1. Place the Bed at player's feet (or below if feet is not air)
        Block bedBlock = blockAtFeet;
        if (bedBlock.getType() != Material.AIR) {
            bedBlock = blockBelow; // Try below
            if (bedBlock.getType() != Material.AIR) {
                // If both feet and below are solid, we might need to find the nearest air block or place on top.
                // For simplicity, we will place on top of the block below.
                bedBlock = blockBelow.getRelative(0, 1, 0);
            }
        }
        
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        // 1. Check if we are in the Plot World.
        if (plugin.getSkyblockWorld() != null && block.getWorld().equals(plugin.getSkyblockWorld())) {
            if (plugin.isStoreChest(block.getLocation())) {
                if (!plugin.removeStoreChest(player, block, false)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "You cannot break another player's store chest.");
                    return;
                }
                player.sendMessage(ChatColor.YELLOW + "Store chest removed.");
            }

            // Prevent breaking the protected bed
            if (isProtectedBed(block)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot break your plot bed!");
            }
            // If it's not a protected block, allow normal breaking (return true allows vanilla behavior)
            return;
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Check if the player is in the Skyblock/Plot world
        if (plugin.getSkyblockWorld() != null && player.getWorld().equals(plugin.getSkyblockWorld())) {
            // Set respawn location to the bed
            Block bedBlock = findBedNearPlayer(player);
            if (bedBlock != null) {
                event.setRespawnLocation(bedBlock.getLocation().add(0, 1, 0));
            }
        }
    }

    private Block findBedNearPlayer(Player player) {
        Location loc = player.getLocation();
        Block blockAtFeet = loc.getBlock();
        Block blockBelow = blockAtFeet.getRelative(0, -1, 0);
        
        if (isProtectedBed(blockAtFeet)) {
            return blockAtFeet;
        } else if (isProtectedBed(blockBelow)) {
            return blockBelow;
        }
        
        // Check nearby blocks (within 5 blocks)
        for (int x = -5; x <= 5; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -5; z <= 5; z++) {
                    Block block = loc.getBlock().getRelative(x, y, z);
                    if (isProtectedBed(block)) {
                        return block;
                    }
                }
            }
        }
        
        return null;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !plugin.isStoreChest(block.getLocation())) {
            return;
        }

        Player player = event.getPlayer();
        if (plugin.canManageStoreChest(player, block.getLocation()) && !player.isSneaking()) {
            return;
        }

        event.setCancelled(true);
        plugin.handleStoreChestInteract(player, block);
    }

    private boolean isProtectedBed(Block block) {
        if (block.getType() == Material.RED_BED) {
            return true;
        }
        return false;
    }

}
