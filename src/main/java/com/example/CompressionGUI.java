package com.example;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CompressionGUI implements InventoryHolder {

    private static final int ROWS = 6;
    private static final int OUTPUT_SLOT = 49;

    private final Inventory inventory;
    private final Player player;

    private static final NamespacedKey COMPRESSED_KEY =
            new NamespacedKey(BuildCompresserFumino.getInstance(), "compressed_count");

    private static final String LORE_PREFIX = "§8┃ ";
    private static final int MAX_STACK_SIZE = 99;

    public CompressionGUI(Player player) {
        this.player = player;
        this.inventory = Bukkit.createInventory(this, ROWS * 9, "§8压缩机");
        fillBorder();
        player.openInventory(inventory);
    }

    private void fillBorder() {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.setDisplayName(" ");
        border.setItemMeta(meta);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i == OUTPUT_SLOT) continue;
            int row = i / 9, col = i % 9;
            if (row == 0 || row == ROWS - 1 || col == 0 || col == 8)
                inventory.setItem(i, border.clone());
        }
    }

    public boolean isInputSlot(int slot) {
        if (slot == OUTPUT_SLOT) return false;
        int row = slot / 9, col = slot % 9;
        return (row >= 1 && row <= 4) && (col >= 1 && col <= 7);
    }

    public boolean isOutputSlot(int slot) { return slot == OUTPUT_SLOT; }

    public boolean isBorderSlot(int slot) {
        if (slot == OUTPUT_SLOT) return false;
        int row = slot / 9, col = slot % 9;
        return row == 0 || row == ROWS - 1 || col == 0 || col == 8;
    }

    // ====================== 新增：判断物品是否可压缩 ======================

    /**
     * 判断物品是否可被压缩。
     * 可压缩条件：是方块（可放置为方块）或红石粉（特殊处理）
     */
    public static boolean isCompressible(Material material) {
        return material.isBlock() || material == Material.REDSTONE || material == Material.STRING;
    }

    public static boolean isCompressible(ItemStack item) {
        return item != null && isCompressible(item.getType());
    }

    // ====================== 核心工具 ======================

    /** 是否压缩物品 */
    public static boolean isCompressed(ItemStack item) {
        return getCompressedCount(item) > 0;
    }

    /** 读取真实压缩数量（PDC） */
    public static int getCompressedCount(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        return item.getItemMeta()
                .getPersistentDataContainer()
                .getOrDefault(COMPRESSED_KEY, PersistentDataType.INTEGER, 0);
    }

    /** 设置真实压缩数量，并同步更新堆叠量 */
    public static void setCompressedCount(ItemStack item, int count) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (count <= 0) {
            meta.getPersistentDataContainer().remove(COMPRESSED_KEY);
        } else {
            meta.getPersistentDataContainer().set(COMPRESSED_KEY, PersistentDataType.INTEGER, count);
        }
        item.setItemMeta(meta);
        item.setAmount(Math.min(count, MAX_STACK_SIZE));
    }

    /** 提取原始物品（移除所有压缩数据） */
    public static ItemStack extractOriginal(ItemStack item) {
        ItemStack original = item.clone();
        ItemMeta meta = original.getItemMeta();
        if (meta == null) return original;
        meta.getPersistentDataContainer().remove(COMPRESSED_KEY);
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null) {
                lore.removeIf(line -> line.startsWith(LORE_PREFIX) || line.trim().isEmpty());
                meta.setLore(lore.isEmpty() ? null : lore);
            }
        }
        meta.setMaxStackSize(new ItemStack(original.getType()).getMaxStackSize());
        original.setItemMeta(meta);
        original.setAmount(1);
        return original;
    }

    /** 构建干净比较样本 */
    private static ItemStack buildCleanSample(ItemStack item) {
        ItemStack sample = item.clone();
        sample.setAmount(1);
        ItemMeta meta = sample.getItemMeta();
        if (meta == null) return sample;
        meta.getPersistentDataContainer().remove(COMPRESSED_KEY);
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null) {
                lore.removeIf(line -> line.startsWith(LORE_PREFIX) || line.trim().isEmpty());
                meta.setLore(lore.isEmpty() ? null : lore);
            }
        }
        meta.setMaxStackSize(sample.getType().getMaxStackSize());
        sample.setItemMeta(meta);
        return sample;
    }

    /** 创建压缩物品 */
    public static ItemStack createCompressedItem(ItemStack original, int totalCount) {
        ItemStack compressed = original.clone();
        int displayAmount = Math.min(totalCount, MAX_STACK_SIZE);
        compressed.setAmount(displayAmount);
        ItemMeta meta = compressed.getItemMeta();
        if (meta == null) return compressed;

        meta.setMaxStackSize(MAX_STACK_SIZE);
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();
        if (lore.isEmpty() || !lore.get(lore.size() - 1).trim().isEmpty()) lore.add("");
        lore.add(LORE_PREFIX + "已压缩: §a" + totalCount + " §7个");
        lore.add(LORE_PREFIX + "§7右键放置消耗一个");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(COMPRESSED_KEY, PersistentDataType.INTEGER, totalCount);
        compressed.setItemMeta(meta);
        return compressed;
    }

    // ====================== 更新输出 ======================

    public void updateOutput() {
        ItemStack originalItem = null;
        ItemStack cleanSample = null;
        int totalCount = 0;

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!isInputSlot(slot)) continue;
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            int count = isCompressed(item) ? getCompressedCount(item) : item.getAmount();
            ItemStack currentSample = buildCleanSample(item);

            if (cleanSample == null) {
                cleanSample = currentSample;
                originalItem = extractOriginal(item);
                totalCount = count;
            } else {
                if (item.getType() != originalItem.getType() || !cleanSample.isSimilar(currentSample)) {
                    inventory.setItem(OUTPUT_SLOT, null);
                    return;
                }
                totalCount += count;
            }
        }

        if (originalItem == null || totalCount <= 0) {
            inventory.setItem(OUTPUT_SLOT, null);
            return;
        }

        inventory.setItem(OUTPUT_SLOT, createCompressedItem(originalItem, totalCount));
        player.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.6f, 1.0f);
    }

    public void consumeInput() {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!isInputSlot(slot)) continue;
            inventory.setItem(slot, null);
        }
        updateOutput();
    }

    public void returnInputItems() {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!isInputSlot(slot)) continue;
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                player.getWorld().dropItem(player.getLocation(), item);
                inventory.setItem(slot, null);
            }
        }
    }

    public static boolean areItemsCombinable(ItemStack a, ItemStack b) {
        if (a.getType() != b.getType()) return false;
        return buildCleanSample(a).isSimilar(buildCleanSample(b));
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    public Player getPlayer() { return player; }
}
