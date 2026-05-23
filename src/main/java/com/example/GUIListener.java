package com.example;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class GUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CompressionGUI gui)) return;
        if (event.getWhoClicked() != gui.getPlayer()) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        Inventory clickedInv = event.getClickedInventory();
        ItemStack cursor = event.getCursor();

        // Shift 点击
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (clickedInv != null && clickedInv.getHolder() instanceof Player) {
                ItemStack shifted = event.getCurrentItem();
                if (shifted != null && !shifted.getType().isAir()) {
                    if (!CompressionGUI.isCompressible(shifted)) {
                        event.setCancelled(true);
                        player.sendMessage("§c只有可放置的方块（以及红石粉）才能被压缩！");
                        return;
                    }
                }
                Bukkit.getScheduler().runTask(BuildCompresserFumino.getInstance(), gui::updateOutput);
                return;
            }
            if (gui.isBorderSlot(slot)) { event.setCancelled(true); return; }
            if (gui.isOutputSlot(slot)) {
                event.setCancelled(true);
                ItemStack output = gui.getInventory().getItem(slot);
                if (output == null || output.getType().isAir()) return;
                ItemStack toGive = output.clone();
                player.getInventory().addItem(toGive).forEach((id, left) ->
                        player.getWorld().dropItem(player.getLocation(), left));
                gui.consumeInput();
                player.updateInventory();
                return;
            }
            Bukkit.getScheduler().runTask(BuildCompresserFumino.getInstance(), gui::updateOutput);
            return;
        }

        if (clickedInv != null && clickedInv.getHolder() instanceof Player) return;

        if (gui.isBorderSlot(slot)) { event.setCancelled(true); return; }

        if (gui.isOutputSlot(slot)) {
            InventoryAction act = event.getAction();
            if (act == InventoryAction.PICKUP_ALL || act == InventoryAction.PICKUP_HALF
                    || act == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
                ItemStack output = gui.getInventory().getItem(slot);
                if (output == null || output.getType().isAir()) return;
                ItemStack toGive = output.clone();
                player.getInventory().addItem(toGive).forEach((id, left) ->
                        player.getWorld().dropItem(player.getLocation(), left));
                gui.consumeInput();
                player.updateInventory();
            } else {
                event.setCancelled(true);
            }
            return;
        }

        if (cursor != null && !cursor.getType().isAir()) {
            if (!CompressionGUI.isCompressible(cursor)) {
                event.setCancelled(true);
                player.sendMessage("§c只有可放置的方块（以及红石粉）才能被压缩！");
                return;
            }
        }

        Bukkit.getScheduler().runTask(BuildCompresserFumino.getInstance(), gui::updateOutput);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CompressionGUI) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CompressionGUI gui) {
            if (event.getPlayer() == gui.getPlayer()) {
                gui.returnInputItems();
            }
        }
    }
}
