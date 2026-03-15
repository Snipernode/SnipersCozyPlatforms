package TSD.Plot;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import TSD.Plot.VoidGenerator;
import TSD.Plot.listeners.BlockInteractListener;
import TSD.Plot.listeners.PlayerListener;
import TSD.Plot.managers.PluginManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public class PlatformsPlugin extends JavaPlugin {

    private static PlatformsPlugin instance;
    private final Map<UUID, AdminSession> adminSessions = new HashMap<>();

    public static final class StoreChestData {
        public final UUID owner;
        public final ItemStack item;
        public final int price;
        public final int amount;

        public StoreChestData(UUID owner, ItemStack item, int price, int amount) {
            this.owner = owner;
            this.item = item;
            this.price = price;
            this.amount = amount;
        }
    }

    public static class AdminSession {
        public final UUID owner;
        public final long startTime;
        public AdminSession(UUID owner, long startTime) {
            this.owner = owner;
            this.startTime = startTime;
        }
    }

    private static final int SIZE = 50;
    private static final int HEIGHT = 200;
    private static final int SPACING = 60; // distance grid spacing

    private World skyblockWorld;

    @Override
    public void onEnable() {
        // Preserve previous main-plugin behavior
        saveDefaultConfig();
        
        // Ensure config has default biome setting
        if (!getConfig().contains("world.biome")) {
            getConfig().set("world.biome", "plains");
            saveConfig();
        }

        // initialize managers and listeners
        PluginManager.getInstance().initialize();
        Bukkit.getPluginManager().registerEvents(new PlayerListener(), this);
        Bukkit.getPluginManager().registerEvents(new BlockInteractListener(this), this);
        
        // register command executor and create world
        getCommand("pfs").setExecutor(this);
        getCommand("plotadmin").setExecutor(this);
        instance = this;
        
        createSkyblockWorld();
        getLogger().info("SnipersComfortablePlots (PlatformsPlugin) has been enabled!");
    }

    private void createSkyblockWorld() {
        // Check if world is already loaded
        if (Bukkit.getWorld("survivalplots") != null) {
            this.skyblockWorld = Bukkit.getWorld("survivalplots");
            getLogger().info("World 'survivalplots' already loaded.");
            return;
        }

        getLogger().info("Creating void world 'survivalplots'...");

        // Create the world using our custom VoidGenerator
        WorldCreator creator = new WorldCreator("survivalplots");
        creator.environment(World.Environment.NORMAL);
        creator.generator(new VoidGenerator()); 
        creator.generateStructures(false); 

        this.skyblockWorld = creator.createWorld();

        if (this.skyblockWorld != null) {
            // Set GameRules
            skyblockWorld.setGameRule(GameRule.KEEP_INVENTORY, true);
            skyblockWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
            skyblockWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
            
            // Set spawn to a safe location in the void
            Location spawn = new Location(skyblockWorld, 0.5, 100, 0.5);
            
            // Ensure the block below spawn is solid
            if (spawn.getBlock().getRelative(0, -1, 0).getType().isAir()) {
                spawn.getBlock().getRelative(0, -1, 0).setType(Material.BEDROCK);
            }
            
            skyblockWorld.setSpawnLocation(spawn);
            
            getLogger().info("Successfully created world 'survivalplots'.");
        } else {
            getLogger().severe("Failed to create world 'survivalplots'!");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players may use this command.");
            return true;
        }
        Player p = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("plotadmin")) {
            return handlePlotAdminCommand(p, args);
        }

        return handlePfsCommand(p, args);
    }

    private boolean handlePfsCommand(Player p, String[] args) {
        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            if (sub.equals("shop")) {
                return handleShopCommand(p, Arrays.copyOfRange(args, 1, args.length));
            }
            if (sub.equals("market")) {
                sendMarketplaceHelp(p);
                return true;
            }
        }

        if (args.length == 0) {
            // teleport to own platform
            teleportToPlatform(p, p.getUniqueId());
            return true;
        }

        if (args.length == 1) {
            String sub = args[0].toLowerCase();
            if (sub.equals("info")) {
                sendPlotInfo(p, p.getUniqueId());
                return true;
            }
            if (sub.equals("trusted")) {
                sendTrustedList(p, p.getUniqueId());
                return true;
            }
            if (sub.equals("where")) {
                sendCurrentPlotInfo(p);
                return true;
            }

            UUID owner = resolvePlayerUuid(args[0]);
            if (owner == null) {
                p.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
            if (!owner.equals(p.getUniqueId()) && !isTrusted(owner, p.getUniqueId()) && !p.hasPermission("snipersplots.admin")) {
                p.sendMessage(ChatColor.RED + "You are not trusted to enter this player's platform.");
                return true;
            }
            teleportToPlatform(p, owner);
            return true;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            UUID otherId = resolvePlayerUuid(args[1]);
            if (otherId == null) {
                p.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
            String otherName = getDisplayName(otherId);
            
            if (sub.equals("trust")) {
                addTrust(p.getUniqueId(), otherId);
                p.sendMessage(ChatColor.GREEN + "Trusted " + otherName);
                return true;
            } else if (sub.equals("untrust")) {
                removeTrust(p.getUniqueId(), otherId);
                p.sendMessage(ChatColor.GREEN + "Untrusted " + otherName);
                return true;
            } else if (sub.equals("join")) {
                if (!isTrusted(otherId, p.getUniqueId())) {
                    p.sendMessage(ChatColor.RED + "You are not trusted to enter this player's platform.");
                    return true;
                }
                teleportToPlatform(p, otherId);
                return true;
            } else if (sub.equals("info")) {
                sendPlotInfo(p, otherId);
                return true;
            } else if (sub.equals("trusted")) {
                if (!p.hasPermission("snipersplots.admin") && !p.getUniqueId().equals(otherId)) {
                    p.sendMessage(ChatColor.RED + "You can only view your own trust list.");
                    return true;
                }
                sendTrustedList(p, otherId);
                return true;
            }
        }

        p.sendMessage(ChatColor.YELLOW + "Usage:");
        p.sendMessage(ChatColor.GRAY + "/pfs " + ChatColor.WHITE + "- Teleport to your plot");
        p.sendMessage(ChatColor.GRAY + "/pfs <player|uuid> " + ChatColor.WHITE + "- Visit a plot you can access");
        p.sendMessage(ChatColor.GRAY + "/pfs trust <player> " + ChatColor.WHITE + "- Grant plot access");
        p.sendMessage(ChatColor.GRAY + "/pfs untrust <player> " + ChatColor.WHITE + "- Remove plot access");
        p.sendMessage(ChatColor.GRAY + "/pfs join <player> " + ChatColor.WHITE + "- Join a plot where you are trusted");
        p.sendMessage(ChatColor.GRAY + "/pfs info [player|uuid] " + ChatColor.WHITE + "- Show plot details");
        p.sendMessage(ChatColor.GRAY + "/pfs trusted [player|uuid] " + ChatColor.WHITE + "- Show trust list");
        p.sendMessage(ChatColor.GRAY + "/pfs where " + ChatColor.WHITE + "- Show the plot you are standing in");
        p.sendMessage(ChatColor.GRAY + "/pfs market " + ChatColor.WHITE + "- Show marketplace help");
        p.sendMessage(ChatColor.GRAY + "/pfs shop ... " + ChatColor.WHITE + "- Manage physical store chests");
        return true;
    }

    private boolean handleShopCommand(Player player, String[] args) {
        if (!getConfig().getBoolean("marketplace.enabled", true)) {
            player.sendMessage(ChatColor.RED + "The marketplace is currently disabled.");
            return true;
        }

        if (args.length == 0) {
            sendMarketplaceHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("collect")) {
            collectMarketplaceEarnings(player);
            return true;
        }

        Block target = getTargetChest(player);
        if ((sub.equals("create") || sub.equals("remove") || sub.equals("info")) && target == null) {
            player.sendMessage(ChatColor.RED + "Look directly at a chest within " + getShopCommandRange() + " blocks.");
            return true;
        }

        if (sub.equals("create")) {
            if (args.length != 3) {
                player.sendMessage(ChatColor.RED + "Usage: /pfs shop create <price> <amount>");
                return true;
            }
            int price;
            int amount;
            try {
                price = Integer.parseInt(args[1]);
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                player.sendMessage(ChatColor.RED + "Price and amount must be whole numbers.");
                return true;
            }
            if (price <= 0 || amount <= 0) {
                player.sendMessage(ChatColor.RED + "Price and amount must both be greater than zero.");
                return true;
            }
            createStoreChest(player, target, price, amount);
            return true;
        }

        if (sub.equals("remove")) {
            removeStoreChest(player, target, true);
            return true;
        }

        if (sub.equals("info")) {
            sendStoreChestInfo(player, target.getLocation());
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Marketplace chest commands:");
        player.sendMessage(ChatColor.GRAY + "/pfs shop create <price> <amount> " + ChatColor.WHITE + "- Turn the chest you're looking at into a store chest");
        player.sendMessage(ChatColor.GRAY + "/pfs shop info " + ChatColor.WHITE + "- Show the selected store chest details");
        player.sendMessage(ChatColor.GRAY + "/pfs shop remove " + ChatColor.WHITE + "- Remove the selected store chest listing");
        player.sendMessage(ChatColor.GRAY + "/pfs shop collect " + ChatColor.WHITE + "- Withdraw your store earnings");
        return true;
    }

    private boolean handlePlotAdminCommand(Player admin, String[] args) {
        if (!admin.hasPermission("snipersplots.admin")) {
            admin.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }

        if (args.length == 0) {
            admin.sendMessage(ChatColor.YELLOW + "Usage:");
            admin.sendMessage(ChatColor.GRAY + "/plotadmin <player|uuid> " + ChatColor.WHITE + "- Teleport to a player's plot (offline supported)");
            admin.sendMessage(ChatColor.GRAY + "/plotadmin name <uuid> " + ChatColor.WHITE + "- Translate UUID to player name");
            admin.sendMessage(ChatColor.GRAY + "/plotadmin uuid <player> " + ChatColor.WHITE + "- Translate player name to UUID");
            return true;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("name") || sub.equals("translate")) {
                UUID targetUuid;
                try {
                    targetUuid = UUID.fromString(args[1]);
                } catch (IllegalArgumentException ex) {
                    admin.sendMessage(ChatColor.RED + "Invalid UUID format.");
                    return true;
                }
                String resolvedName = getDisplayName(targetUuid);
                admin.sendMessage(ChatColor.GREEN + targetUuid.toString() + ChatColor.GRAY + " -> " + ChatColor.GREEN + resolvedName);
                return true;
            }

            if (sub.equals("uuid")) {
                UUID resolved = resolvePlayerUuid(args[1]);
                if (resolved == null) {
                    admin.sendMessage(ChatColor.RED + "Could not resolve player '" + args[1] + "'.");
                    return true;
                }
                admin.sendMessage(ChatColor.GREEN + args[1] + ChatColor.GRAY + " -> " + ChatColor.GREEN + resolved.toString());
                return true;
            }
        }

        if (args.length == 1) {
            UUID target = resolvePlayerUuid(args[0]);
            if (target == null) {
                admin.sendMessage(ChatColor.RED + "Could not resolve player '" + args[0] + "'.");
                return true;
            }

            teleportToPlatform(admin, target);
            admin.sendMessage(ChatColor.GRAY + "Target UUID: " + ChatColor.WHITE + target);
            return true;
        }

        admin.sendMessage(ChatColor.RED + "Usage: /plotadmin <player|uuid> | /plotadmin name <uuid> | /plotadmin uuid <player>");
        return true;
    }

    private UUID resolvePlayerUuid(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            // Not a UUID, try name resolution.
        }

        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return online.getUniqueId();
        }

        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            String name = offlinePlayer.getName();
            if (name != null && name.equalsIgnoreCase(input)) {
                return offlinePlayer.getUniqueId();
            }
        }

        OfflinePlayer fallback = Bukkit.getOfflinePlayer(input);
        if (fallback != null && (fallback.hasPlayedBefore() || fallback.isOnline())) {
            return fallback.getUniqueId();
        }

        return null;
    }

    private String getDisplayName(UUID uuid) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        String name = offlinePlayer != null ? offlinePlayer.getName() : null;
        return name != null ? name : uuid.toString();
    }

    private int getShopCommandRange() {
        return Math.max(2, getConfig().getInt("marketplace.shop-range", 6));
    }

    private Material getMarketplaceCurrency() {
        String configured = getConfig().getString("marketplace.currency", "EMERALD");
        try {
            return Material.valueOf(configured.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Material.EMERALD;
        }
    }

    private String getMarketplaceCurrencyName() {
        return prettifyMaterial(getMarketplaceCurrency());
    }

    private String prettifyMaterial(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private String describeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "Air";
        }
        if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return prettifyMaterial(item.getType());
    }

    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private String formatMessage(String template, UUID owner) {
        return colorize(template
            .replace("%owner%", getDisplayName(owner))
            .replace("%uuid%", owner.toString()));
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    private void sendMarketplaceHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "---------------- Marketplace ----------------");
        player.sendMessage(ChatColor.YELLOW + "Currency: " + ChatColor.WHITE + getMarketplaceCurrencyName());
        player.sendMessage(ChatColor.GRAY + "1. Stand in your plot and look at a chest.");
        player.sendMessage(ChatColor.GRAY + "2. Hold the item you want to sell.");
        player.sendMessage(ChatColor.GRAY + "3. Run /pfs shop create <price> <amount>.");
        player.sendMessage(ChatColor.GRAY + "4. Stock that chest with the matching item.");
        player.sendMessage(ChatColor.GRAY + "Buyers right-click your store chest to purchase a bundle.");
        player.sendMessage(ChatColor.GRAY + "Use /pfs shop collect to withdraw your earnings.");
        player.sendMessage(ChatColor.GOLD + "---------------------------------------------");
    }

    private Location computePlatformOrigin(UUID owner) {
        if (skyblockWorld == null) {
            return null;
        }

        int hash = owner.hashCode();
        int centerX = Math.floorMod(hash, 1000) * SPACING;
        int centerZ = Math.floorMod(hash >>> 10, 1000) * SPACING;
        return new Location(skyblockWorld, centerX + 0.5, HEIGHT + 1, centerZ + 0.5);
    }

    public Location getPlatformPreviewLocation(UUID owner) {
        Location existing = getPlatformOrigin(owner);
        return existing != null ? existing : computePlatformOrigin(owner);
    }

    private String getStoreChestPath(Location loc) {
        return "marketplace.shops." + loc.getWorld().getUID() + "." + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    public StoreChestData getStoreChest(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }

        String path = getStoreChestPath(loc);
        String ownerRaw = getConfig().getString(path + ".owner");
        ItemStack item = getConfig().getItemStack(path + ".item");
        int price = getConfig().getInt(path + ".price");
        int amount = getConfig().getInt(path + ".amount");
        if (ownerRaw == null || item == null || price <= 0 || amount <= 0) {
            return null;
        }

        try {
            return new StoreChestData(UUID.fromString(ownerRaw), item.clone(), price, amount);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isStoreChest(Location loc) {
        return getStoreChest(loc) != null;
    }

    private Block getTargetChest(Player player) {
        Block target = player.getTargetBlockExact(getShopCommandRange());
        if (target == null || !(target.getState() instanceof Chest)) {
            return null;
        }
        return target;
    }

    public boolean canManageStoreChest(Player player, Location loc) {
        StoreChestData shop = getStoreChest(loc);
        return shop != null && (shop.owner.equals(player.getUniqueId()) || player.hasPermission("snipersplots.admin"));
    }

    public void createStoreChest(Player player, Block block, int price, int amount) {
        if (!(block.getState() instanceof Chest)) {
            player.sendMessage(ChatColor.RED + "That block is not a chest.");
            return;
        }
        if (!isPlotWorld(block.getWorld())) {
            player.sendMessage(ChatColor.RED + "Store chests can only be created in the plot world.");
            return;
        }

        UUID ownerAt = getOwnerAtLocation(block.getLocation());
        if (ownerAt == null || !ownerAt.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You can only create a store chest on your own plot.");
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "Hold the item you want to sell in your main hand.");
            return;
        }

        ItemStack product = hand.clone();
        product.setAmount(1);

        String path = getStoreChestPath(block.getLocation());
        getConfig().set(path + ".owner", player.getUniqueId().toString());
        getConfig().set(path + ".item", product);
        getConfig().set(path + ".price", price);
        getConfig().set(path + ".amount", amount);
        saveConfig();

        player.sendMessage(ChatColor.GREEN + "Store chest created for " + amount + "x " + describeItem(product)
            + ChatColor.GREEN + " at " + price + " " + getMarketplaceCurrencyName() + ".");
        player.playSound(block.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.6f, 1.3f);
        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.8, 0.5), 10, 0.3, 0.3, 0.3, 0.02);
    }

    public boolean removeStoreChest(Player player, Block block, boolean announce) {
        StoreChestData shop = getStoreChest(block.getLocation());
        if (shop == null) {
            if (announce) {
                player.sendMessage(ChatColor.RED + "That chest is not registered as a store chest.");
            }
            return false;
        }
        if (!canManageStoreChest(player, block.getLocation())) {
            if (announce) {
                player.sendMessage(ChatColor.RED + "You do not own that store chest.");
            }
            return false;
        }

        getConfig().set(getStoreChestPath(block.getLocation()), null);
        saveConfig();

        if (announce) {
            player.sendMessage(ChatColor.YELLOW + "Store chest removed.");
            player.playSound(block.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.7f, 0.8f);
        }
        return true;
    }

    public void sendStoreChestInfo(Player player, Location loc) {
        StoreChestData shop = getStoreChest(loc);
        if (shop == null) {
            player.sendMessage(ChatColor.RED + "That chest is not a registered store chest.");
            return;
        }

        int stock = getStoreChestStock(loc, shop);
        player.sendMessage(ChatColor.GOLD + "--------------- Store Chest ---------------");
        player.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE + getDisplayName(shop.owner));
        player.sendMessage(ChatColor.YELLOW + "Item: " + ChatColor.WHITE + describeItem(shop.item));
        player.sendMessage(ChatColor.YELLOW + "Bundle: " + ChatColor.WHITE + shop.amount);
        player.sendMessage(ChatColor.YELLOW + "Price: " + ChatColor.WHITE + shop.price + " " + getMarketplaceCurrencyName());
        player.sendMessage(ChatColor.YELLOW + "Stock: " + ChatColor.WHITE + stock);
        if (canManageStoreChest(player, loc)) {
            player.sendMessage(ChatColor.YELLOW + "Pending earnings: " + ChatColor.WHITE + getMarketplaceBalance(shop.owner)
                + " " + getMarketplaceCurrencyName());
        }
        player.sendMessage(ChatColor.GOLD + "------------------------------------------");
    }

    private int getStoreChestStock(Location loc, StoreChestData shop) {
        if (!(loc.getBlock().getState() instanceof Chest chest)) {
            return 0;
        }
        return countMatchingItems(chest.getInventory(), shop.item);
    }

    private int countMatchingItems(Inventory inventory, ItemStack template) {
        int total = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.isSimilar(template)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void removeMatchingItems(Inventory inventory, ItemStack template, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || !stack.isSimilar(template)) {
                continue;
            }
            int taken = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - taken);
            if (stack.getAmount() <= 0) {
                inventory.setItem(slot, null);
            } else {
                inventory.setItem(slot, stack);
            }
            remaining -= taken;
        }
    }

    private int countMaterial(PlayerInventory inventory, Material material) {
        int total = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void removeMaterial(PlayerInventory inventory, Material material, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int taken = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - taken);
            if (stack.getAmount() <= 0) {
                inventory.setItem(slot, null);
            } else {
                inventory.setItem(slot, stack);
            }
            remaining -= taken;
        }
    }

    private void giveItem(Player player, ItemStack template, int totalAmount) {
        int remaining = totalAmount;
        while (remaining > 0) {
            ItemStack stack = template.clone();
            stack.setAmount(Math.min(remaining, stack.getMaxStackSize()));
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            for (ItemStack extra : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
            remaining -= stack.getAmount();
        }
    }

    private int getMarketplaceBalance(UUID owner) {
        return Math.max(0, getConfig().getInt("marketplace.balances." + owner, 0));
    }

    private void setMarketplaceBalance(UUID owner, int amount) {
        getConfig().set("marketplace.balances." + owner, Math.max(0, amount));
        saveConfig();
    }

    private void addMarketplaceBalance(UUID owner, int amount) {
        setMarketplaceBalance(owner, getMarketplaceBalance(owner) + amount);
    }

    private void collectMarketplaceEarnings(Player player) {
        int balance = getMarketplaceBalance(player.getUniqueId());
        if (balance <= 0) {
            player.sendMessage(ChatColor.YELLOW + "You have no marketplace earnings to collect.");
            return;
        }

        Material currency = getMarketplaceCurrency();
        setMarketplaceBalance(player.getUniqueId(), 0);
        giveItem(player, new ItemStack(currency), balance);
        player.sendMessage(ChatColor.GREEN + "Collected " + balance + " " + getMarketplaceCurrencyName() + " from your store chests.");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.15f);
    }

    public void handleStoreChestInteract(Player player, Block block) {
        StoreChestData shop = getStoreChest(block.getLocation());
        if (shop == null) {
            return;
        }

        if (canManageStoreChest(player, block.getLocation())) {
            sendStoreChestInfo(player, block.getLocation());
            return;
        }

        Chest chest = (Chest) block.getState();
        int stock = countMatchingItems(chest.getInventory(), shop.item);
        if (stock < shop.amount) {
            player.sendMessage(ChatColor.RED + "This store chest is out of stock.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            return;
        }

        Material currency = getMarketplaceCurrency();
        if (countMaterial(player.getInventory(), currency) < shop.price) {
            player.sendMessage(ChatColor.RED + "You need " + shop.price + " " + getMarketplaceCurrencyName() + " to buy this bundle.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            return;
        }

        removeMaterial(player.getInventory(), currency, shop.price);
        removeMatchingItems(chest.getInventory(), shop.item, shop.amount);
        giveItem(player, shop.item, shop.amount);
        addMarketplaceBalance(shop.owner, shop.price);

        player.sendMessage(ChatColor.GREEN + "Purchased " + shop.amount + "x " + describeItem(shop.item)
            + ChatColor.GREEN + " for " + shop.price + " " + getMarketplaceCurrencyName() + ".");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.9f, 1.05f);
        player.getWorld().spawnParticle(Particle.WAX_ON, block.getLocation().add(0.5, 0.9, 0.5), 14, 0.3, 0.3, 0.3, 0.02);

        Player owner = Bukkit.getPlayer(shop.owner);
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(ChatColor.GOLD + player.getName() + " bought " + shop.amount + "x "
                + describeItem(shop.item) + ChatColor.GOLD + " for " + shop.price + " " + getMarketplaceCurrencyName() + ".");
            sendActionBar(owner, ChatColor.GOLD + "Marketplace sale: +" + shop.price + " " + getMarketplaceCurrencyName());
        }
    }

    public void showPlotTransition(Player player, UUID fromOwner, UUID toOwner) {
        if (toOwner == null || toOwner.equals(fromOwner)) {
            return;
        }
        if (!getConfig().getBoolean("effects.transitions.enabled", true)) {
            return;
        }

        String template = toOwner.equals(player.getUniqueId())
            ? getConfig().getString("messages.enter-own-plot", "&aEntering your plot")
            : getConfig().getString("messages.enter-other-plot", "&bEntering %owner%'s plot");
        String actionBar = formatMessage(template, toOwner);
        sendActionBar(player, actionBar);

        Location loc = player.getLocation().clone().add(0.0, 1.0, 0.0);
        if (getConfig().getBoolean("effects.transitions.particles", true)) {
            player.getWorld().spawnParticle(Particle.END_ROD, loc, 12, 0.45, 0.6, 0.45, 0.01);
        }
        if (getConfig().getBoolean("effects.transitions.sound", true)) {
            player.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, toOwner.equals(player.getUniqueId()) ? 1.2f : 0.9f);
        }
    }

    public void playBoundaryEffects(Player player, UUID owner) {
        if (!getConfig().getBoolean("effects.boundary.enabled", true)) {
            return;
        }

        Location loc = player.getLocation().clone().add(0.0, 1.0, 0.0);
        player.getWorld().spawnParticle(Particle.CLOUD, loc, 24, 0.55, 0.4, 0.55, 0.02);
        player.getWorld().spawnParticle(Particle.END_ROD, loc, 16, 0.45, 0.7, 0.45, 0.01);

        if (getConfig().getBoolean("effects.boundary.sound", true)) {
            player.playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.85f, 1.2f);
        }

        sendActionBar(player, formatMessage(
            getConfig().getString("messages.boundary-hit", "&cBoundary reached. Returning to %owner%'s origin."),
            owner
        ));
    }

    private void playTeleportEffects(Player player, UUID owner) {
        if (!getConfig().getBoolean("effects.teleport.enabled", true)) {
            return;
        }

        Location loc = player.getLocation().clone().add(0.0, 1.0, 0.0);
        if (getConfig().getBoolean("effects.teleport.particles", true)) {
            player.getWorld().spawnParticle(Particle.PORTAL, loc, 48, 0.7, 0.9, 0.7, 0.15);
            player.getWorld().spawnParticle(Particle.END_ROD, loc, 28, 0.55, 0.85, 0.55, 0.02);
        }
        if (getConfig().getBoolean("effects.teleport.sound", true)) {
            player.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, owner.equals(player.getUniqueId()) ? 1.1f : 0.95f);
        }
        if (getConfig().getBoolean("effects.teleport.titles", true)) {
            String titleTemplate = owner.equals(player.getUniqueId())
                ? getConfig().getString("messages.teleport-own-title", "&aYour Plot")
                : getConfig().getString("messages.teleport-visit-title", "&b%owner%'s Plot");
            String subtitleTemplate = owner.equals(player.getUniqueId())
                ? getConfig().getString("messages.teleport-own-subtitle", "&7Welcome back")
                : getConfig().getString("messages.teleport-visit-subtitle", "&7Visiting %owner%'s platform");
            player.sendTitle(
                formatMessage(titleTemplate, owner),
                formatMessage(subtitleTemplate, owner),
                5,
                30,
                10
            );
        }
    }

    private void sendPlotInfo(Player player, UUID owner) {
        Location plotLoc = getPlatformPreviewLocation(owner);
        int trustedCount = getConfig().getStringList("trust." + owner).size();

        player.sendMessage(ChatColor.GOLD + "---------------- Plot Info ----------------");
        player.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE + getDisplayName(owner));
        player.sendMessage(ChatColor.YELLOW + "UUID: " + ChatColor.WHITE + owner);
        if (plotLoc != null) {
            player.sendMessage(ChatColor.YELLOW + "World: " + ChatColor.WHITE + plotLoc.getWorld().getName());
            player.sendMessage(ChatColor.YELLOW + "Origin: " + ChatColor.WHITE
                + plotLoc.getBlockX() + ", " + plotLoc.getBlockY() + ", " + plotLoc.getBlockZ());
        }
        player.sendMessage(ChatColor.YELLOW + "Trusted Players: " + ChatColor.WHITE + trustedCount);
        player.sendMessage(ChatColor.GOLD + "-------------------------------------------");
    }

    private void sendTrustedList(Player player, UUID owner) {
        List<String> stored = getConfig().getStringList("trust." + owner);
        if (stored.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + getDisplayName(owner) + "'s trusted list is empty.");
            return;
        }

        List<String> names = new ArrayList<>();
        for (String entry : stored) {
            try {
                names.add(getDisplayName(UUID.fromString(entry)));
            } catch (IllegalArgumentException ex) {
                names.add(entry);
            }
        }

        player.sendMessage(ChatColor.GOLD + getDisplayName(owner) + "'s trusted players:");
        player.sendMessage(ChatColor.YELLOW + String.join(ChatColor.GRAY + ", " + ChatColor.YELLOW, names));
    }

    private void sendCurrentPlotInfo(Player player) {
        if (!isPlotWorld(player.getWorld())) {
            player.sendMessage(ChatColor.RED + "You are not in the plot world.");
            return;
        }

        UUID owner = getOwnerAtLocation(player.getLocation());
        if (owner == null) {
            player.sendMessage(ChatColor.YELLOW + "You are currently between plots.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "You are standing in " + getDisplayName(owner) + "'s plot.");
        sendActionBar(player, formatMessage("&eCurrent plot: &f%owner%", owner));
    }

    private void teleportToPlatform(Player who, UUID owner) {
        Location loc = getOrCreatePlatformLocation(owner);
        who.teleport(loc);
        playTeleportEffects(who, owner);
        
        // Check if this is the owner visiting their own plot for the first time (or just visiting)
        // We send the welcome message if it's their own platform.
        if (owner.equals(who.getUniqueId())) {
            who.sendMessage(ChatColor.GOLD + "------------------------------------------------");
            who.sendMessage(ChatColor.YELLOW + " The Chest in front of you will supply you with the blocks you've mined in the overworld");
            who.sendMessage(ChatColor.YELLOW + " use the bed to set your spawn and fight off phantoms");
            who.sendMessage(ChatColor.GOLD + "------------------------------------------------");
        } else {
            who.sendMessage("Teleported to " + getDisplayName(owner) + "'s platform.");
            
            // If an admin teleports to another player's platform, record an admin inspection session
            if (who.hasPermission("snipersplots.admin")) {
                adminSessions.put(who.getUniqueId(), new AdminSession(owner, System.currentTimeMillis()));
                who.sendMessage(ChatColor.YELLOW + "You are now inspecting " + getDisplayName(owner) + "'s plot. Editing is disabled while inspecting.");
                Bukkit.getLogger().info("Admin " + who.getName() + " began inspecting " + getDisplayName(owner) + "'s plot.");
            }
        }
    }

    private Location getOrCreatePlatformLocation(UUID owner) {
        Location existing = getPlatformOrigin(owner);
        if (existing != null) {
            return existing;
        }

        Location computed = computePlatformOrigin(owner);
        if (computed == null) {
            return null;
        }

        int centerX = computed.getBlockX();
        int centerZ = computed.getBlockZ();
        int groundY = computed.getBlockY() - 1;

        buildPlatform(skyblockWorld, centerX, groundY, centerZ);

        String base = "platforms." + owner.toString();
        getConfig().set(base + ".world", skyblockWorld.getName());
        getConfig().set(base + ".x", centerX + 0.5);
        getConfig().set(base + ".y", groundY);
        getConfig().set(base + ".z", centerZ + 0.5);
        saveConfig();

        return computed;
    }

    public Location getPlatformOrigin(UUID owner) {
        String base = "platforms." + owner.toString();
        if (!getConfig().contains(base + ".world")) {
            return null;
        }

        String worldName = getConfig().getString(base + ".world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        double x = getConfig().getDouble(base + ".x");
        double y = getConfig().getDouble(base + ".y");
        double z = getConfig().getDouble(base + ".z");
        return new Location(world, x, y + 1, z);
    }

    public boolean isPlotWorld(World world) {
        return skyblockWorld != null && world != null && skyblockWorld.equals(world);
    }

    @SuppressWarnings("unused")
    private void buildPlatform(World world, int centerX, int y, int centerZ) {
        int half = SIZE / 2;
        int xs = centerX - half;
        int zs = centerZ - half;
        
        // Create platform base
        for (int x = xs; x < xs + SIZE; x++) {
            for (int z = zs; z < zs + SIZE; z++) {
                world.getBlockAt(x, y, z).setType(Material.GRASS_BLOCK);
                world.getBlockAt(x, y - 1, z).setType(Material.DIRT);
                world.getBlockAt(x, y - 2, z).setType(Material.STONE);
            }
        }
    }

    public boolean isTrusted(UUID owner, UUID other) {
        List<String> list = getConfig().getStringList("trust." + owner.toString());
        return list.contains(other.toString());
    }

    public void addTrust(UUID owner, UUID other) {
        String path = "trust." + owner.toString();
        List<String> list = getConfig().getStringList(path);
        if (!list.contains(other.toString())) {
            list.add(other.toString());
            getConfig().set(path, list);
            saveConfig();
        }
    }

    public void removeTrust(UUID owner, UUID other) {
        String path = "trust." + owner.toString();
        List<String> list = getConfig().getStringList(path);
        if (list.contains(other.toString())) {
            list.remove(other.toString());
            getConfig().set(path, list);
            saveConfig();
        }
    }

    public static PlatformsPlugin getInstance() {
        return instance;
    }
    
    public World getSkyblockWorld() {
        return skyblockWorld;
    }

    // Return owner UUID for platform that contains this location, or null
    public UUID getOwnerAtLocation(Location loc) {
        if (loc == null) return null;
        if (loc.getWorld() == null) return null;
        if (!loc.getWorld().getName().equals(skyblockWorld != null ? skyblockWorld.getName() : "")) return null;
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        int half = SIZE / 2;
        if (getConfig().getConfigurationSection("platforms") == null) return null;
        for (String key : getConfig().getConfigurationSection("platforms").getKeys(false)) {
            String base = "platforms." + key;
            double cx = getConfig().getDouble(base + ".x", Double.NaN);
            double cz = getConfig().getDouble(base + ".z", Double.NaN);
            if (Double.isNaN(cx) || Double.isNaN(cz)) continue;
            int centerX = (int) Math.floor(cx);
            int centerZ = (int) Math.floor(cz);
            int xs2 = centerX - half;
            int zs2 = centerZ - half;
            if (x >= xs2 && x < xs2 + SIZE && z >= zs2 && z < zs2 + SIZE) {
                try {
                    return UUID.fromString(key);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
            }
        }
        return null;
    }

    public AdminSession getAdminSession(UUID admin) {
        return adminSessions.get(admin);
    }

    public boolean isPlotBeingInspected(UUID owner) {
        for (AdminSession s : adminSessions.values()) {
            if (s.owner.equals(owner)) return true;
        }
        return false;
    }

    public void endAdminSession(UUID admin) {
        adminSessions.remove(admin);
    }
}
