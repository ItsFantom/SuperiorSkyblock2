package com.bgsoftware.superiorskyblock.menu;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.utils.threads.SuperiorThread;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class SuperiorMenu implements InventoryHolder {

    protected static final SuperiorSkyblockPlugin plugin = SuperiorSkyblockPlugin.getPlugin();
    protected static Map<UUID, SuperiorMenu> previousMenus = new HashMap<>();

    private String identifier;
    protected Sound openSound = null, closeSound = null;

    public SuperiorMenu(String identifier){
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    public Sound getOpenSound() {
        return openSound;
    }

    public Sound getCloseSound() {
        return closeSound;
    }

    public void setOpenSound(Sound openSound) {
        this.openSound = openSound;
    }

    public void setCloseSound(Sound closeSound) {
        this.closeSound = closeSound;
    }

    @Override
    public abstract Inventory getInventory();

    public abstract void onClick(InventoryClickEvent e);

    public void openInventory(SuperiorPlayer superiorPlayer, SuperiorMenu previousMenu){
        if(openSound != null)
            superiorPlayer.asPlayer().playSound(superiorPlayer.getLocation(), openSound, 1, 1);

        if(previousMenu != null)
            previousMenus.put(superiorPlayer.getUniqueId(), previousMenu);

        new SuperiorThread(() -> {
            Inventory inventory = getInventory();
            Bukkit.getScheduler().runTask(plugin, () -> superiorPlayer.asPlayer().openInventory(inventory));
        }).start();
    }

    public void closeInventory(SuperiorPlayer superiorPlayer){
        SuperiorMenu previousMenu = previousMenus.get(superiorPlayer.getUniqueId());
        previousMenus.remove(superiorPlayer.getUniqueId());

        if(closeSound != null && (previousMenu == null || previousMenu.openSound == null))
            superiorPlayer.asPlayer().playSound(superiorPlayer.getLocation(), closeSound, 1, 1);

        if(previousMenu != null)
            Bukkit.getScheduler().runTask(plugin, () -> previousMenu.openInventory(superiorPlayer, null));
    }

    protected static Sound getSound(String name){
        try{
            return Sound.valueOf(name);
        }catch(Exception ex){
            return null;
        }
    }

}