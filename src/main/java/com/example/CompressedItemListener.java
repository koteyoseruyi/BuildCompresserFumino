package com.example;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

public class CompressedItemListener implements Listener {

    /** 副手在 PlayerInventory 中的 slot 索引 */
    private static final int OFFHAND_SLOT = 40;

    private final Map<UUID, PendingPlacement> pendingMap = new HashMap<>();

    private record PendingPlacement(ItemStack item, int slot) {}

    // ===================================================================
    // 放置处理
    // ===================================================================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlacePre(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        boolean isOffHand = event.getHand() == EquipmentSlot.OFF_HAND;

        ItemStack placingItem;
        if (isOffHand) {
            placingItem = player.getInventory().getItemInOffHand();
        } else {
            placingItem = event.getItemInHand();
        }

        if (CompressionGUI.isCompressed(placingItem)) {
            int slot = isOffHand ? OFFHAND_SLOT : player.getInventory().getHeldItemSlot();
            pendingMap.put(player.getUniqueId(), new PendingPlacement(placingItem.clone(), slot));
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

        // 检查副手
        ItemStack offhand = inv.getItem(OFFHAND_SLOT);
        if (offhand != null && offhand.getType() == type && CompressionGUI.isCompressed(offhand)) {
            pendingMap.put(player.getUniqueId(), new PendingPlacement(offhand.clone(), OFFHAND_SLOT));
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
            if (pending.slot() == OFFHAND_SLOT) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItem(pending.slot(), null);
            }
            player.updateInventory();
            return;
        }

        ItemStack newItem = CompressionGUI.createCompressedItem(
                CompressionGUI.extractOriginal(pending.item()), newCount
        );

        if (pending.slot() == OFFHAND_SLOT) {
            player.getInventory().setItemInOffHand(newItem);
        } else {
            player.getInventory().setItem(pending.slot(), newItem);
        }
        player.updateInventory();
    }

    // ===================================================================
    // 禁止任何形式的拆分 + 背包/储存容器内允许 Shift+点击移动
    // ===================================================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        Inventory clickedInv = event.getClickedInventory();

        if (current != null && CompressionGUI.isCompressed(current)) {
            // ── 禁止右键拆分 ──
            if (event.getAction() == InventoryAction.PICKUP_HALF) {
                event.setCancelled(true);
                return;
            }

            // ── Shift+点击处理 ──
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                Inventory topInv = event.getView().getTopInventory();
                if (topInv.getHolder() instanceof Player) {
                    return; // 背包界面，允许
                }
                if (isAllowedStorageContainer(topInv.getType())) {
                    return; // 储存容器，允许
                }
                event.setCancelled(true); // 其他情况，禁止
                return;
            }

            // ── ✨ 阻止从合成格取出压缩物品（防止拆分） ──
            if (event.getSlotType() == InventoryType.SlotType.CRAFTING
                    && clickedInv != null && !(clickedInv.getHolder() instanceof Player)) {
                // 压缩物品在合成格中 → 阻止手动取出，自动归还到背包
                event.setCancelled(true);
                ItemStack item = current.clone();
                // 用 InventoryView.setItem 清理合成格，不触发底层事件
                event.getView().setItem(event.getRawSlot(), null);
                Player player = (Player) event.getWhoClicked();
                player.getInventory().addItem(item).forEach((id, left) ->
                        player.getWorld().dropItem(player.getLocation(), left));
                return;
            }
        }

        // ── 阻止压缩物品被放入合成格 ──
        if (cursor != null && !cursor.getType().isAir() && CompressionGUI.isCompressed(cursor)) {
            if (event.getSlotType() == InventoryType.SlotType.CRAFTING) {
                event.setCancelled(true);
                return;
            }

            if (event.getAction() == InventoryAction.PLACE_ONE) {
                event.setCancelled(true);
                return;
            }
            if (event.getAction() == InventoryAction.PLACE_SOME) {
                event.setCancelled(true);
                return;
            }
            if (event.getAction() == InventoryAction.PLACE_ALL) {
                if (current != null && !current.getType().isAir()
                        && !CompressionGUI.areItemsCombinable(cursor, current)) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
                if (clickedInv != null && !(clickedInv.getHolder() instanceof Player)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        ItemStack oldCursor = event.getOldCursor();
        if (oldCursor != null && CompressionGUI.isCompressed(oldCursor)) {
            event.setCancelled(true);
            return;
        }

        for (int slot : event.getRawSlots()) {
            Inventory inv = event.getView().getInventory(slot);
            if (inv == null) continue;
            ItemStack item = inv.getItem(slot);
            if (item != null && CompressionGUI.isCompressed(item)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ===================================================================
    // 禁止压缩物品进入漏斗/漏斗矿车
    // ===================================================================

    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        if (item != null && CompressionGUI.isCompressed(item)) {
            event.setCancelled(true);
        }
    }

    // ===================================================================
    // 禁止投掷器/发射器弹出压缩物品
    // ===================================================================

    @EventHandler
    public void onBlockDispense(BlockDispenseEvent event) {
        ItemStack item = event.getItem();
        if (item != null && CompressionGUI.isCompressed(item)) {
            event.setCancelled(true);
        }
    }

    // ===================================================================
    // 禁止丢弃压缩物品
    // ===================================================================

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (CompressionGUI.isCompressed(dropped)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c压缩物品不能被丢弃！");
        }
    }

    // ===================================================================
    // 禁止放入展示框
    // ===================================================================

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!CompressionGUI.isCompressed(item)) return;

        EntityType entityType = event.getRightClicked().getType();
        if (entityType == EntityType.ITEM_FRAME || entityType == EntityType.GLOW_ITEM_FRAME) {
            event.setCancelled(true);
            player.sendMessage("§c压缩物品不能被放入展示框！");
        }
    }

    // ===================================================================
    // 阻止右键交互
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

        if (isInteractiveBlock(type)) {
            event.setCancelled(true);
            return;
        }

        if (!item.getType().isBlock() && item.getType() != Material.REDSTONE && item.getType() != Material.STRING) {
            event.setCancelled(true);
        }
    }

    // ===================================================================
    // ✨ 最终修复：阻止合成 + 自动归还合成格中的压缩物品
    // 使用 InventoryView.setItem 避免递归
    // ===================================================================

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        var inv = event.getInventory();
        boolean hasCompressed = false;
        for (ItemStack item : inv.getMatrix()) {
            if (item != null && CompressionGUI.isCompressed(item)) {
                hasCompressed = true;
                break;
            }
        }

        if (!hasCompressed) return;

        // 禁止合成
        inv.setResult(null);

        // ── 使用 InventoryView.setItem 从合成格清除压缩物品并归还玩家 ──
        // InventoryView.setItem 修改的是客户端视图，不会通过
        // TransientCraftingContainer.setItem 触发 slotsChanged，
        // 因此不会产生递归 PrepareItemCraftEvent
        Player player = (Player) event.getView().getPlayer();
        var view = player.getOpenInventory();

        for (int i = 0; i < inv.getMatrix().length; i++) {
            ItemStack item = inv.getMatrix()[i];
            if (item != null && CompressionGUI.isCompressed(item)) {
                // 合成矩阵在 InventoryView 中从 raw slot 1 开始（slot 0 是合成结果）
                int rawSlot = i + 1;
                view.setItem(rawSlot, null);
                player.getInventory().addItem(item).forEach((id, left) ->
                        player.getWorld().dropItem(player.getLocation(), left));
            }
        }
    }

    // ===================================================================
    // 辅助
    // ===================================================================

    private boolean isAllowedStorageContainer(InventoryType type) {
        return type == InventoryType.CHEST
                || type == InventoryType.BARREL
                || type == InventoryType.SHULKER_BOX;
    }

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
