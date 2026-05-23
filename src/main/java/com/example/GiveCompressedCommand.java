package com.example;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.command.CommandExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GiveCompressedCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c用法: /givecompressed <玩家> <物品> <数量>");
            return true;
        }

        // 解析玩家
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§c玩家 '" + args[0] + "' 不存在或不在线！");
            return true;
        }

        // 解析物品类型
        Material material = Material.matchMaterial(args[1]);
        if (material == null) {
            // 尝试加 minecraft: 前缀
            material = Material.matchMaterial("minecraft:" + args[1]);
        }
        if (material == null) {
            sender.sendMessage("§c未知物品 '" + args[1] + "'！");
            return true;
        }

        // 检查是否可压缩
        if (!CompressionGUI.isCompressible(material)) {
            sender.sendMessage("§c物品 '" + args[1] + "' 不可被压缩！只有可放置为方块的物品才能压缩。");
            return true;
        }

        // 解析数量
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c数量必须是一个有效的数字！");
            return true;
        }

        if (amount < 1 || amount > 32767) {
            sender.sendMessage("§c数量必须在 1 ~ 32767 之间！");
            return true;
        }

        // 创建压缩物品
        ItemStack base = new ItemStack(material, 1);
        // 对于红石粉等特殊物品，需要确保能获得正确的物品形式
        if (material == Material.REDSTONE) {
            base = new ItemStack(Material.REDSTONE, 1);
        }
        ItemStack compressed = CompressionGUI.createCompressedItem(base, amount);

        // 给予玩家
        target.getInventory().addItem(compressed).forEach((index, leftover) -> {
            target.getWorld().dropItem(target.getLocation(), leftover);
        });
        target.updateInventory();

        sender.sendMessage("§a已给予 " + target.getName() + " " + amount + " 个压缩 " + formatMaterialName(material) + "！");
        if (!sender.equals(target)) {
            target.sendMessage("§a你获得了 " + amount + " 个压缩 " + formatMaterialName(material) + "！");
        }
        return true;
    }

    // ====================== Tab 补全 ======================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            // 补全玩家名
            String prefix = args[0].toLowerCase();
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    players.add(p.getName());
                }
            }
            return players;
        }

        if (args.length == 2) {
            // 补全可压缩的物品名
            String prefix = args[1].toLowerCase();
            List<String> materials = new ArrayList<>();
            for (Material mat : Material.values()) {
                if (!CompressionGUI.isCompressible(mat)) continue;
                String name = mat.getKey().getKey(); // 小写名称，如 "stone", "redstone"
                if (name.startsWith(prefix)) {
                    materials.add(name);
                }
            }
            return materials;
        }

        if (args.length == 3) {
            // 提示数量范围
            return List.of("数量(1-32767)");
        }

        return List.of();
    }

    /** 格式化物品名为中文友好格式 */
    private String formatMaterialName(Material material) {
        String key = material.getKey().getKey();
        // 将下划线替换为空格，首字母大写
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : key.toCharArray()) {
            if (c == '_') {
                sb.append(' ');
                capitalize = true;
            } else if (capitalize) {
                sb.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
