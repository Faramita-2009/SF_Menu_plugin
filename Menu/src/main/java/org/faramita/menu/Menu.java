package org.faramita.menu;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.DisplaySlot;
import org.jetbrains.annotations.NotNull;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.*;

public class Menu extends JavaPlugin implements CommandExecutor {

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("未找到 PlaceholderAPI，部分功能可能无法使用！");
        }

        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        // 注册命令处理器
        Objects.requireNonNull(getCommand("menu")).setExecutor(this);  // 注册 /menu 命令
        Objects.requireNonNull(getCommand("hub")).setExecutor(this);   // 注册 /hub 命令

        // 启动定时任务，每 1 秒检查玩家是否掉落到指定 Y 坐标
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkPlayerFall(player);
                }
            }
        }.runTaskTimer(this, 0L, 20L);

        getLogger().info("插件已启用！");

        MenuListener listener = new MenuListener(this);
        getServer().getPluginManager().registerEvents(listener, this);

        // 注册命令
        Objects.requireNonNull(getCommand("hub")).setExecutor(this);
        Objects.requireNonNull(getCommand("menu")).setExecutor(this);

        // 设置命令补全
        Objects.requireNonNull(getCommand("hub")).setTabCompleter((sender, cmd, alias, args) -> {
            if (args.length == 1 && sender.hasPermission("hub.set")) {
                return Collections.singletonList("set");
            }
            return Collections.emptyList();
        });
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        // 检查命令发送者是否是玩家
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以执行此命令！");
            return true;
        }

        // 将发送者转换为玩家对象

        // 处理 /menu 命令
        if (command.getName().equalsIgnoreCase("menu")) {
            if (args.length == 0) {
                // 如果玩家输入 /menu，打开菜单
                openMenu(player);
                return true;
            } else if (args[0].equalsIgnoreCase("reload")) {
                // 如果玩家输入 /menu reload，重新加载配置文件
                reloadConfig();
                player.sendMessage(ChatColor.GREEN + "配置文件已重新加载！");
                return true;
            } else if (args[0].equalsIgnoreCase("update")) {
                // 如果玩家输入 /menu update，更新侧边栏和 Tab 菜单
                updateSidebar(player);
                updateTabMenu(player);
                player.sendMessage(ChatColor.GREEN + "侧边栏和 Tab 菜单已更新！");
                return true;
            } else if (args[0].equalsIgnoreCase("?")) {
                // 如果玩家输入 /menu ?，显示帮助信息
                for (String helpMessage : getConfig().getStringList("help-messages")) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', helpMessage));
                }
                return true;
            }
        }

        // 在 onCommand 方法中修改/hub命令处理
        if (command.getName().equalsIgnoreCase("hub")) {
            if (args.length == 0) {
                teleportToLobby(player);
                return true;
            } else if (args[0].equalsIgnoreCase("set")) {
                if (!player.hasPermission("hub.set")) {
                    player.sendMessage(ChatColor.RED + "你没有权限设置大厅位置！");
                    return true;
                }

                Location loc;
                if (args.length == 6) { // 指定坐标
                    try {
                        loc = new Location(
                                player.getWorld(),
                                Double.parseDouble(args[1]),
                                Double.parseDouble(args[2]),
                                Double.parseDouble(args[3]),
                                Float.parseFloat(args[4]),
                                Float.parseFloat(args[5])
                        );
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "坐标格式错误！");
                        return true;
                    }
                } else { // 使用当前位置
                    loc = player.getLocation();
                }

                // 保存到配置
                getConfig().set("hub.location.world", Objects.requireNonNull(loc.getWorld()).getName());
                getConfig().set("hub.location.x", loc.getX());
                getConfig().set("hub.location.y", loc.getY());
                getConfig().set("hub.location.z", loc.getZ());
                getConfig().set("hub.location.yaw", loc.getYaw());
                getConfig().set("hub.location.pitch", loc.getPitch());
                saveConfig();

                player.sendMessage(ChatColor.GREEN + "大厅位置已设置为: " +
                        loc.getWorld().getName() + ", " +
                        loc.getX() + ", " + loc.getY() + ", " + loc.getZ());
                return true;
            }
            return false;
        }
        return false;
    }

    @Override
    public void onDisable() {
        getLogger().info("插件已禁用！");
    }

    private void checkPlayerFall(Player player) {
        // 仅检查指定世界
        if (!player.getWorld().getName().equals(
                getConfig().getString("hub.location.world", "Lobby"))) {
            return;
        }

        // 检查Y坐标
        if (player.getLocation().getY() <
                getConfig().getInt("fall-protection.min-y", -30)) {
            teleportToLobby(player);
        }
    }

    private void teleportToLobby(Player player) {
        Location lobbyLoc = new Location(
                Bukkit.getWorld(getConfig().getString("hub.location.world", "Lobby")),
                getConfig().getDouble("hub.location.x", 0.5),
                getConfig().getDouble("hub.location.y", 15),
                getConfig().getDouble("hub.location.z", 0.5),
                (float) getConfig().getDouble("hub.location.yaw", 90),
                (float) getConfig().getDouble("hub.location.pitch", 0)
        );

        player.teleport(lobbyLoc);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("fall-protection.message",
                        "&a差点被虚空娘吞了！")));
    }

    public void updateSidebar(Player player) {
        String allowedWorld = getConfig().getString("default-world", "world");
        if (!player.getWorld().getName().equals(allowedWorld)) {
            return;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            getLogger().severe("ScoreboardManager 为 null，无法创建计分板！");
            return;
        }

        // 为每个玩家创建独立的计分板
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        player.setScoreboard(board);

        Objective objective = board.getObjective("sidebar");
        if (objective == null) {
            objective = board.registerNewObjective("sidebar", Criteria.DUMMY, ChatColor.translateAlternateColorCodes('&', getConfig().getString("sidebar.title", "&9浮帆&f服务器")));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            for (String entry : board.getEntries()) {
                board.resetScores(entry);
            }
        }

    }

    public void updateTabMenu(Player player) {
        String allowedWorld = getConfig().getString("default-world", "world");
        if (!player.getWorld().getName().equals(allowedWorld)) {
            return;
        }

        String header = ChatColor.translateAlternateColorCodes('&', getConfig().getString("tab.header", "&9浮帆&f服务器"));
        String footer = ChatColor.translateAlternateColorCodes('&', getConfig().getString("tab.footer", "&e延迟: %ping% ms").replace("%ping%", String.valueOf(player.getPing())));

        player.setPlayerListHeaderFooter(header, footer);
    }

    public void openMenu(Player player) {
        try {
            Inventory menu = Bukkit.createInventory(player, 27, getMenuTitle());

            ConfigurationSection itemsSection = getConfig().getConfigurationSection("menu.items");
            if (itemsSection == null) {
                player.sendMessage(ChatColor.RED + "菜单配置错误：未找到物品列表！");
                return;
            }

            for (String itemKey : itemsSection.getKeys(false)) {
                String materialName = getConfig().getString("menu.items." + itemKey + ".material");
                Material material = null;
                if (materialName != null) {
                    material = Material.matchMaterial(materialName);
                }
                if (material == null) {
                    getLogger().warning("无效的物品材质: " + materialName);
                    continue;
                }

                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                if (meta == null) continue;

                // 设置动态标题和Lore
                meta.setDisplayName(replaceVariables(
                        player,
                        getConfig().getString("menu.items." + itemKey + ".title", "未命名")
                ));

                List<String> lore = new ArrayList<>();
                for (String line : getConfig().getStringList("menu.items." + itemKey + ".lore")) {
                    lore.add(replaceVariables(player, line));
                }
                meta.setLore(lore);

                // 处理玩家头颅
                if (material == Material.PLAYER_HEAD && meta instanceof SkullMeta) {
                    handlePlayerHead(player, itemKey, (SkullMeta) meta);
                }

                item.setItemMeta(meta);

                int slot = getConfig().getInt("menu.items." + itemKey + ".slot", 0);
                if (slot >= 0 && slot < 27) {
                    menu.setItem(slot, item);
                }
            }

            player.openInventory(menu);
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "打开菜单时出错，请联系管理员。");
            getLogger().severe("打开菜单时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handlePlayerHead(Player player, String itemKey, SkullMeta skullMeta) {
        String skullOwner = replaceVariables(
                player,
                getConfig().getString("menu.items." + itemKey + ".skull-owner", "")
        );

        try {
            // 尝试使用1.16+的新API
            Method method = Bukkit.class.getMethod("getOfflinePlayerIfCached", String.class);
            OfflinePlayer offlinePlayer = (OfflinePlayer) method.invoke(null, skullOwner);
            if (offlinePlayer != null) {
                skullMeta.setOwningPlayer(offlinePlayer);
                return;
            }
        } catch (Exception ignored) {}

        // 回退方案
        try {
            // 尝试使用setOwningPlayer
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(skullOwner));
        } catch (Exception e) {
            // 最终回退到旧方法
            skullMeta.setOwner(skullOwner);
        }
    }
    // 删除旧的 replaceVariables 方法，只保留以下版本：
    private String replaceVariables(Player player, String text) {
        if (text == null || text.isEmpty()) return "";

        // 优先使用 PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            text = PlaceholderAPI.setPlaceholders(player, text);
        }

        // 基础变量回退
        return ChatColor.translateAlternateColorCodes('&', text)
                .replace("%player_name%", player.getName())
                .replace("%player_world%", player.getWorld().getName());
    }

    private String getMenuTitle() {
        return ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("menu.title", "&e服务器菜单"));
    }

    public class MenuListener implements Listener {
        private final Menu plugin;

        public MenuListener(Menu plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            checkAndGiveMenuItem(player); // 检查并给予海洋之心
            plugin.updateSidebar(player); // 初始化侧边栏
            plugin.updateTabMenu(player); // 初始化 Tab 菜单
        }

        @EventHandler
        public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
            Player player = event.getPlayer();
            checkAndGiveMenuItem(player); // 检查并给予海洋之心
            plugin.updateSidebar(player); // 更新侧边栏
            plugin.updateTabMenu(player); // 更新 Tab 菜单
        }

        @EventHandler
        public void onPlayerRespawn(PlayerRespawnEvent event) {
            Player player = event.getPlayer();
            checkAndGiveMenuItem(player); // 检查并给予海洋之心
            plugin.updateSidebar(player); // 更新侧边栏
            plugin.updateTabMenu(player); // 更新 Tab 菜单
        }

        @EventHandler
        public void onPlayerDeath(PlayerDeathEvent event) {
            Player player = event.getEntity();
            checkAndGiveMenuItem(player); // 检查并给予海洋之心
        }

        @EventHandler
        public void onPlayerDropItem(PlayerDropItemEvent event) {
            ItemStack item = event.getItemDrop().getItemStack();
            if (item.getType() == Material.HEART_OF_THE_SEA) {
                event.setCancelled(true); // 阻止丢弃
                event.getPlayer().sendMessage(ChatColor.RED + "你不能丢弃这个物品！");
            }
        }

        @EventHandler
        public void onPlayerInteract(PlayerInteractEvent event) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                ItemStack item = event.getItem();
                if (item != null && item.getType() == Material.HEART_OF_THE_SEA) {
                    plugin.openMenu(event.getPlayer()); // 调用 openMenu 方法
                    event.setCancelled(true); // 防止丢弃物品
                }
            }
        }

        @EventHandler
        public void onInventoryClick(InventoryClickEvent event) {
            if (event.getView().getTitle().equals(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("menu.title", "&e服务器菜单")))) {
                event.setCancelled(true); // 防止玩家移动物品
                Player player = (Player) event.getWhoClicked();
                ItemStack clickedItem = event.getCurrentItem();

                if (clickedItem != null) {
                    for (Map.Entry<String, Object> entry : Objects.requireNonNull(plugin.getConfig().getConfigurationSection("menu.items")).getValues(false).entrySet()) {
                        String itemKey = entry.getKey();
                        Material material = Material.valueOf(plugin.getConfig().getString("menu.items." + itemKey + ".material"));
                        String command = plugin.getConfig().getString("menu.items." + itemKey + ".command");
                        String url = plugin.getConfig().getString("menu.items." + itemKey + ".url"); // 新增：获取链接

                        if (clickedItem.getType() == material) {
                            if (command != null) {
                                player.performCommand(command); // 执行命令
                            } else if (url != null) {
                                // 发送可点击的链接消息（需要客户端支持）
                                player.sendMessage(ChatColor.YELLOW + "点击链接访问: " + ChatColor.UNDERLINE + url);
                                // 或者通过 Spigot 的 API 发送可点击消息（推荐）

                                player.spigot().sendMessage(new ComponentBuilder("点击访问官网: ")
                                        .color(net.md_5.bungee.api.ChatColor.GREEN)
                                        .append(url)
                                        .color(net.md_5.bungee.api.ChatColor.BLUE)
                                        .underlined(true)  // 注意这里是 underlined() 不是 underline()
                                        .event(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                                        .create());
                            }
                            player.closeInventory(); // 关闭菜单
                            break;
                        }
                    }
                }
            }
        }
        @EventHandler
        public void onBlockBreak(BlockBreakEvent event) {
            if (getConfig().getBoolean("hub.build-protection", true) &&
                    event.getPlayer().getWorld().getName().equals(
                            getConfig().getString("hub.location.world", "Lobby")) &&
                    !event.getPlayer().hasPermission("hub.build")) {
                event.setCancelled(true);
            }
        }
        @EventHandler
        public void onPlayerMove(PlayerMoveEvent event) {
            Player player = event.getPlayer();
            if (!getConfig().getBoolean("double-jump.enabled", true)) return;

            // 检测是否在大厅世界
            if (!player.getWorld().getName().equals(
                    getConfig().getString("hub.location.world", "Lobby"))) return;

            // 检测是否在地面
            if (player.getLocation().subtract(0, 0.1, 0).getBlock().getType().isSolid() &&
                    !player.isFlying()) {
                player.setAllowFlight(true);
            }
        }
        @EventHandler
        public void onPlayerFly(PlayerToggleFlightEvent event) {
            Player player = event.getPlayer();

            // 基础检查
            if (!getConfig().getBoolean("double-jump.enabled", true) ||
                    player.getGameMode() == GameMode.CREATIVE) {
                return;
            }

            // 世界检查
            if (!player.getWorld().getName().equals(
                    getConfig().getString("hub.location.world", "Lobby"))) {
                return;
            }

            event.setCancelled(true);
            player.setAllowFlight(false);

            // 执行飞行
            Vector boost = player.getLocation().getDirection()
                    .multiply(getConfig().getInt("double-jump.horizontal-distance", 10))
                    .setY(getConfig().getInt("double-jump.vertical-height", 5));
            player.setVelocity(boost);

            // 播放音效
            try {
                player.playSound(player.getLocation(),
                        Sound.valueOf(getConfig().getString("double-jump.sound")),
                        1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                player.playSound(player.getLocation(),
                        Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.0f);
            }
        }

        private void checkAndGiveMenuItem(Player player) {
            String allowedWorld = plugin.getConfig().getString("default-world", "world");
            if (player.getWorld().getName().equals(allowedWorld)) {
                giveMenuItem(player);
            }
        }

        private void giveMenuItem(Player player) {
            Material menuItem = Material.valueOf(plugin.getConfig().getString("menu.item", "HEART_OF_THE_SEA"));

            if (!player.getInventory().contains(menuItem)) {
                ItemStack menuItemStack = new ItemStack(menuItem);
                ItemMeta meta = menuItemStack.getItemMeta();

                if (meta != null) {
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("menu.title", "&e服务器菜单")));

                    String loreString = plugin.getConfig().getString("menu.lore", "&f欢迎加入浮帆服务器");
                    List<String> lore = new ArrayList<>();
                    if (!loreString.isEmpty()) {
                        lore.add(ChatColor.translateAlternateColorCodes('&', loreString));
                    }
                    meta.setLore(lore);

                    menuItemStack.setItemMeta(meta);
                    player.getInventory().addItem(menuItemStack);
                } else {
                    plugin.getLogger().warning("无法为物品设置 ItemMeta，物品类型可能不支持。");
                }
            }
        }
    }
}