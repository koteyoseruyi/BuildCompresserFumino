package com.example;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class BuildCompresserFumino extends JavaPlugin {

    private static BuildCompresserFumino instance;

    @Override
    public void onEnable() {
        instance = this;

        // 注册压缩机指令
        getCommand("compress").setExecutor(new CompressionCommand());

        // 注册管理员给予指令（含 Tab 补全）
        GiveCompressedCommand giveCmd = new GiveCompressedCommand();
        getCommand("givecompressed").setExecutor(giveCmd);
        getCommand("givecompressed").setTabCompleter(giveCmd);

        // 注册事件监听器
        Bukkit.getPluginManager().registerEvents(new GUIListener(), this);
        Bukkit.getPluginManager().registerEvents(new CompressedItemListener(), this);

        getLogger().info("BuildCompresserFumino 已启动！");
    }

    @Override
    public void onDisable() {
        getLogger().info("BuildCompresserFumino 已关闭！");
    }

    public static BuildCompresserFumino getInstance() {
        return instance;
    }
}
