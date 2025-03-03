package org.faramita.menu;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.DisplaySlot;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Menu extends JavaPlugin implements CommandExecutor {

    @Override
    public void onEnable() {
        // 检查 PlaceholderAPI 是否存在
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("未找到 PlaceholderAPI，部分功能可能无法使用！");
        }

        // 保存默认配置文件
        saveDefaultConfig();

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        // 注册指令
        if (getCommand("menu") != null) {
            Objects.requireNonNull(getCommand("menu")).setExecutor(this);
        } else {
            getLogger().warning("未找到指令 'menu'，请检查 plugin.yml 文件！");
        }

        if (getCommand("hub") != null) {
            Objects.requireNonNull(getCommand("hub")).setExecutor(this);
        } else {
            getLogger().warning("未找到指令 'hub'，请检查 plugin.yml 文件！");
        }

        // 启动定时任务
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (isWorldEnabled(player.getWorld().getName())) {
                        updateSidebar(player);
                        updateTabMenu(player);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L); // 每 1 秒更新一次

        getLogger().info("插件已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().info("插件已禁用！");
    }

    // 更新侧边栏
    private void updateSidebar(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            getLogger().severe("ScoreboardManager 为 null，无法创建计分板！");
            return;
        }

        Scoreboard board = player.getScoreboard();

        Objective objective = board.getObjective("sidebar");
        if (objective == null) {
            objective = board.registerNewObjective("sidebar", Criteria.DUMMY, ChatColor.translateAlternateColorCodes('&', getConfig().getString("sidebar.title", "&9浮帆&f服务器")));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            // 清除旧的计分板内容
            for (String entry : board.getEntries()) {
                board.resetScores(entry);
            }
        }

        // 获取侧边栏内容
        List<String> content = getConfig().getStringList("sidebar.content");
        if (content.isEmpty()) {
            // 默认内容
            content = Arrays.asList(
                    "&7%date%",
                    "&e欢迎, &f%player_name%",
                    "&e余额: &f%xconomy_balance%"
            );
        }

        // 更新变量部分
        for (int i = 0; i < content.size(); i++) {
            String line = replaceVariables(player, content.get(i));
            objective.getScore(ChatColor.translateAlternateColorCodes('&', line)).setScore(content.size() - i);
        }
    }

    // 更新 Tab 菜单
    private void updateTabMenu(Player player) {
        String header = ChatColor.translateAlternateColorCodes('&', getConfig().getString("tab.header", "&9浮帆&f服务器"));
        String footer = ChatColor.translateAlternateColorCodes('&', getConfig().getString("tab.footer", "&e延迟: %ping% ms").replace("%ping%", String.valueOf(player.getPing())));

        player.setPlayerListHeaderFooter(header, footer);
    }

    // 替换变量
    private String replaceVariables(Player player, String text) {
        // 如果 PlaceholderAPI 存在，使用它替换变量
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }

        // 如果 PlaceholderAPI 不存在，使用 Bukkit 原生变量替换
        return text.replace("%player_name%", player.getName())
                .replace("%ping%", String.valueOf(player.getPing()));
    }

    // 检查世界是否启用
    private boolean isWorldEnabled(String worldName) {
        List<String> enabledWorlds = getConfig().getStringList("enabled-worlds");
        return enabledWorlds.contains(worldName);
    }

    // 指令处理
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以执行此命令！");
            return false;
        }

        if (command.getName().equalsIgnoreCase("menu")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (player.hasPermission("menu.reload")) {
                    reloadConfig();
                    player.sendMessage(ChatColor.GREEN + "配置文件已重新加载！");
                    return true;
                } else {
                    player.sendMessage(ChatColor.RED + "你没有权限执行此命令！");
                    return false;
                }
            }
            openMenu(player);
            return true;
        } else if (command.getName().equalsIgnoreCase("hub")) {
            teleportToHub(player);
            return true;
        }
        return false;
    }

    // 打开服务器菜单
    private void openMenu(Player player) {
        Inventory menu = Bukkit.createInventory(player, 27, ChatColor.translateAlternateColorCodes('&', getConfig().getString("menu.title", "&e服务器菜单")));

        // 添加返回主岛的钟
        ItemStack bell = new ItemStack(Material.BELL);
        ItemMeta bellMeta = bell.getItemMeta();
        if (bellMeta != null) {
            bellMeta.setDisplayName(ChatColor.GOLD + "返回主岛");
            bellMeta.setLore(List.of(ChatColor.GRAY + "点击快速返回主岛"));
            bell.setItemMeta(bellMeta);
        }
        menu.setItem(0, bell); // 将钟放在第一格

        // 添加菜单项（金锭）
        ItemStack goldIngot = new ItemStack(Material.GOLD_INGOT);
        ItemMeta goldMeta = goldIngot.getItemMeta();
        if (goldMeta != null) {
            goldMeta.setDisplayName(ChatColor.GOLD + "查看你的余额");
        }
        goldIngot.setItemMeta(goldMeta);
        menu.setItem(11, goldIngot); // 将金锭放在第 2 行第 2 列（槽位 11）

        player.openInventory(menu);
    }

    // 传送玩家到大厅
    private void teleportToHub(Player player) {
        World hubWorld = Bukkit.getWorld(getConfig().getString("hub.world", "Lobby"));
        if (hubWorld == null) {
            player.sendMessage(ChatColor.RED + "大厅世界未找到！");
            return;
        }

        Location hubLocation = new Location(
                hubWorld,
                getConfig().getDouble("hub.x", 0),
                getConfig().getDouble("hub.y", 15),
                getConfig().getDouble("hub.z", 0),
                (float) getConfig().getDouble("hub.yaw", 90),
                (float) getConfig().getDouble("hub.pitch", 0)
        );

        player.teleport(hubLocation);
        player.sendMessage(ChatColor.GREEN + "已传送至大厅！");
    }

    // 事件监听器类
    public static class MenuListener implements Listener {
        private final Menu plugin;

        public MenuListener(Menu plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            giveMenuItem(event.getPlayer());
        }

        @EventHandler
        public void onPlayerRespawn(PlayerRespawnEvent event) {
            giveMenuItem(event.getPlayer());
        }

        private void giveMenuItem(Player player) {
            Material menuItem = Material.valueOf(plugin.getConfig().getString("menu.item", "HEART_OF_THE_SEA"));

            // 检查玩家是否有菜单物品
            if (!player.getInventory().contains(menuItem)) {
                ItemStack menuItemStack = new ItemStack(menuItem);
                ItemMeta meta = menuItemStack.getItemMeta();

                if (meta != null) {
                    // 设置物品标题
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("menu.title", "&e服务器菜单")));

                    // 设置物品描述
                    String loreString = plugin.getConfig().getString("menu.lore", "&f欢迎加入浮帆服务器");
                    List<String> lore = new ArrayList<>();
                    if (!loreString.isEmpty()) {
                        lore.add(ChatColor.translateAlternateColorCodes('&', loreString));
                    }
                    meta.setLore(lore);

                    // 添加附魔光效
                    if (plugin.getConfig().getBoolean("menu.enchantment", true)) {
                        meta.addEnchant(Enchantment.LUCK, 1, true); // 添加附魔光效
                        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS); // 隐藏附魔描述
                    }

                    menuItemStack.setItemMeta(meta);
                    player.getInventory().addItem(menuItemStack);
                } else {
                    plugin.getLogger().warning("无法为物品设置 ItemMeta，物品类型可能不支持。");
                }
            }
        }

        @EventHandler
        public void onPlayerInteract(PlayerInteractEvent event) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                ItemStack item = event.getItem();
                if (item != null && item.getType() == Material.HEART_OF_THE_SEA) {
                    Player player = event.getPlayer();
                    plugin.openMenu(player);
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
                    if (clickedItem.getType() == Material.GOLD_INGOT) {
                        player.performCommand("money"); // 执行 /money 命令
                        player.closeInventory(); // 关闭菜单界面
                    } else if (clickedItem.getType() == Material.BELL) {
                        player.performCommand("hub"); // 执行 /hub 命令
                        player.closeInventory(); // 关闭菜单界面
                    }
                }
            }
        }
    }
}
