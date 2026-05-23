package com.example;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

public class CompressedItemListener implements Listener {

    private final Map<UUID, PendingPlacement> pendingMap = new HashMap<>();

    private record PendingPlacement(ItemStack item, int slot) {}

    // ===================================================================
    // 放置处理
    // ===================================================================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlacePre(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack hand = event.getItemInHand();

        if (CompressionGUI.isCompressed(hand)) {
            int slot = player.getInventory().getHeldItemSlot();
            pendingMap.put(player.getUniqueId(), new PendingPlacement(hand.clone(), slot));
            return;
        }

        Material type = event.getBlock().getType();
        if (!type.isBlock() && type != Material.REDSTONE && type != Material.STRING) return;

        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() != type) continue;
            if (!CompressionGUI.isCompressed(item)) continue;
            pendingMap.put(player.getUniqueId(), new PendingPlacement(item.clone(), i));
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlacePost(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        PendingPlacement pending = pendingMap.remove(player.getUniqueId());
        if (pending == null) return;

        int oldCount = CompressionGUI.getCompressedCount(pending.item());
        if (oldCount <= 0) return;

        int newCount = oldCount - 1;

        if (newCount <= 0) {
            player.getInventory().setItem(pending.slot(), null);
            player.updateInventory();
            return;
        }

        ItemStack newItem = CompressionGUI.createCompressedItem(
                CompressionGUI.extractOriginal(pending.item()), newCount
        );
        player.getInventory().setItem(pending.slot(), newItem);
        player.updateInventory();
    }

    // ===================================================================
    // 禁止拆分压缩物品（不可拆分）
    // ===================================================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getAction() == InventoryAction.PICKUP_HALF) {
            ItemStack current = event.getCurrentItem();
            if (current != null && CompressionGUI.isCompressed(current)) {
                event.setCancelled(true);
            }
        }
    }

    // ===================================================================
    // 阻止右键交互（工作台/箱子等）
    // 但允许红石粉等非方块物品正常放置
    // ===================================================================

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!CompressionGUI.isCompressed(item)) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (event.getClickedBlock() == null) return;

        Material type = event.getClickedBlock().getType();

        // 点击可交互方块 → 阻止（防止打开箱子/工作台）
        if (isInteractiveBlock(type)) {
            event.setCancelled(true);
            return;
        }

        // 物品既不是方块也不是红石粉 → 阻止右键
        if (!item.getType().isBlock() && item.getType() != Material.REDSTONE) {
            event.setCancelled(true);
        }
    }

    // ===================================================================
    // 阻止合成
    // ===================================================================

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item != null && CompressionGUI.isCompressed(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    // ===================================================================
    // 辅助
    // ===================================================================

    private boolean isInteractiveBlock(Material type) {
        return switch (type) {
            case CRAFTING_TABLE, FURNACE, BLAST_FURNACE, SMOKER,
                 CHEST, TRAPPED_CHEST, BARREL, SHULKER_BOX,
                 ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL,
                 GRINDSTONE, STONECUTTER, CARTOGRAPHY_TABLE,
                 LOOM, BREWING_STAND, ENCHANTING_TABLE,
                 BEACON, HOPPER, DROPPER, DISPENSER,
                 JUKEBOX, RESPAWN_ANCHOR, LECTERN,
                 COMPOSTER, DECORATED_POT,
                 DAYLIGHT_DETECTOR, NOTE_BLOCK,
                 REPEATER, COMPARATOR,
                 BELL, BEEHIVE, BEE_NEST,
                 CAULDRON, WATER_CAULDRON, LAVA_CAULDRON,
                 CONDUIT, END_PORTAL_FRAME,
                 FLETCHING_TABLE, SMITHING_TABLE,
                 SWEET_BERRY_BUSH, CAVE_VINES, CAVE_VINES_PLANT,
                 CAMPFIRE, SOUL_CAMPFIRE,
                 DRAGON_EGG, END_CRYSTAL -> true;
            default -> false;
        };
    }
}
