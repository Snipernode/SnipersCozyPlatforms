package TSD.Plot.listeners;

import TSD.Plot.PlatformsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;

public class PlayerListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // no-op for now
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        PlatformsPlugin plugin = PlatformsPlugin.getInstance();
        if (plugin == null) return;
        Location loc = event.getBlock().getLocation();
        java.util.UUID owner = plugin.getOwnerAtLocation(loc);
        if (owner != null) {
            // if plot is being inspected, disallow edits by non-owner
            if (plugin.isPlotBeingInspected(owner)) {
                if (!p.getUniqueId().equals(owner)) {
                    event.setCancelled(true);
                    p.sendMessage(ChatColor.RED + "This plot is currently under admin inspection; editing is disabled.");
                    return;
                }
            }
            // if this player is an admin currently inspecting any plot, disallow editing while inspecting
            if (plugin.getAdminSession(p.getUniqueId()) != null) {
                event.setCancelled(true);
                p.sendMessage(ChatColor.RED + "You cannot edit while inspecting a player's plot.");
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        PlatformsPlugin plugin = PlatformsPlugin.getInstance();
        if (plugin == null) return;
        Location loc = event.getBlock().getLocation();
        java.util.UUID owner = plugin.getOwnerAtLocation(loc);
        if (owner != null) {
            if (plugin.isPlotBeingInspected(owner)) {
                if (!p.getUniqueId().equals(owner)) {
                    event.setCancelled(true);
                    p.sendMessage(ChatColor.RED + "This plot is currently under admin inspection; editing is disabled.");
                    return;
                }
            }
            if (plugin.getAdminSession(p.getUniqueId()) != null) {
                event.setCancelled(true);
                p.sendMessage(ChatColor.RED + "You cannot edit while inspecting a player's plot.");
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        PlatformsPlugin plugin = PlatformsPlugin.getInstance();
        if (plugin == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        PlatformsPlugin.AdminSession sess = plugin.getAdminSession(p.getUniqueId());
        if (sess != null) {
            // If the admin moved outside the inspected plot, end the session
            java.util.UUID ownerAt = plugin.getOwnerAtLocation(to);
            if (ownerAt == null || !ownerAt.equals(sess.owner)) {
                long durationMs = System.currentTimeMillis() - sess.startTime;
                long secs = durationMs / 1000;
                long mins = secs / 60;
                secs = secs % 60;
                String ownerName = Bukkit.getOfflinePlayer(sess.owner).getName();
                String msg = "Admin " + p.getName() + " inspected " + ownerName + "'s plot for " + mins + " minutes " + secs + " seconds.";
                Bukkit.getLogger().info(msg);
                p.sendMessage(ChatColor.GREEN + "Inspection ended: " + mins + "m " + secs + "s");
                plugin.endAdminSession(p.getUniqueId());
            }
        }

        // Enforce origin-point relocation only inside the plot world.
        if (!plugin.isPlotWorld(to.getWorld()) || from.getWorld() == null || !plugin.isPlotWorld(from.getWorld())) {
            return;
        }

        // Keep admins free to move between plots/worlds.
        if (p.hasPermission("snipersplots.admin")) {
            return;
        }

        // Ignore purely rotational movement to cut event spam.
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        java.util.UUID fromOwner = plugin.getOwnerAtLocation(from);
        java.util.UUID toOwner = plugin.getOwnerAtLocation(to);

        // If a player leaves a plot into the gap/void area, send them back to that plot's origin.
        if (fromOwner != null && toOwner == null) {
            plugin.playBoundaryEffects(p, fromOwner);
            Location origin = plugin.getPlatformOrigin(fromOwner);
            if (origin != null) {
                p.teleport(origin);
            }
            return;
        }

        if (toOwner != null && !toOwner.equals(fromOwner)) {
            plugin.showPlotTransition(p, fromOwner, toOwner);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        PlatformsPlugin plugin = PlatformsPlugin.getInstance();
        if (plugin == null) return;
        PlatformsPlugin.AdminSession sess = plugin.getAdminSession(p.getUniqueId());
        if (sess == null) return;
        long durationMs = System.currentTimeMillis() - sess.startTime;
        long secs = durationMs / 1000;
        long mins = secs / 60;
        secs = secs % 60;
        String ownerName = Bukkit.getOfflinePlayer(sess.owner).getName();
        String msg = "Admin " + p.getName() + " inspected " + ownerName + "'s plot for " + mins + " minutes " + secs + " seconds (quit).";
        Bukkit.getLogger().info(msg);
        plugin.endAdminSession(p.getUniqueId());
    }
}
