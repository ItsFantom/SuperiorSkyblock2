package com.bgsoftware.superiorskyblock.menu;

import com.bgsoftware.superiorskyblock.Locale;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.utils.FileUtil;
import org.bukkit.Bukkit;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public final class IslandCreationMenu extends SuperiorMenu {

    private static Inventory inventory = null;

    private static Map<String, Object> schematicsData = new HashMap<>();

    private SuperiorPlayer superiorPlayer;

    private IslandCreationMenu(){
        super("islandCreationPage");
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        for(String schematic : plugin.getSchematics().getSchematics()){
            if(schematicsData.containsKey(schematic + "-slot")) {
                int slot = get(schematic + "-slot", Integer.class);
                String permission = get(schematic + "-permission", String.class);

                if (superiorPlayer.hasPermission(permission) && slot == e.getRawSlot()) {
                    BigDecimal bonusWorth = new BigDecimal(get(schematic + "-bonus", Long.class));
                    Biome biome = Biome.valueOf(get(schematic + "-biome", String.class));
                    superiorPlayer.asPlayer().closeInventory();
                    Locale.ISLAND_CREATE_PROCCESS_REQUEST.send(superiorPlayer);
                    plugin.getGrid().createIsland(superiorPlayer, schematic, bonusWorth, biome);
                    break;
                }
            }
        }
    }

    @Override
    public void openInventory(SuperiorPlayer superiorPlayer, SuperiorMenu previousMenu) {
        this.superiorPlayer = superiorPlayer;
        super.openInventory(superiorPlayer, previousMenu);
    }

    @Override
    public Inventory getInventory() {
        Inventory inv = Bukkit.createInventory(this, inventory.getSize(), inventory.getTitle());
        inv.setContents(inventory.getContents());

        for(String schematic : plugin.getSchematics().getSchematics()){
            if(schematicsData.containsKey(schematic + "-has-access-item")) {
                String permission = get(schematic + "-permission", String.class);
                String schematicItemKey = superiorPlayer.hasPermission(permission) ? schematic + "-has-access-item" : schematic + "-no-access-item";
                ItemStack schematicItem = get(schematicItemKey, ItemStack.class);
                int slot = get(schematic + "-slot", Integer.class);
                inv.setItem(slot, schematicItem);
            }
        }

       return inv;
    }

    private static <T> T get(String key, Class<T> type){
        return type.cast(schematicsData.get(key));
    }

    public static void init(){
        IslandCreationMenu islandCreationMenu = new IslandCreationMenu();
        File file = new File(plugin.getDataFolder(), "guis/creation-gui.yml");

        if(!file.exists())
            FileUtil.saveResource("guis/creation-gui.yml");

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        inventory = FileUtil.loadGUI(islandCreationMenu, cfg.getConfigurationSection("creation-gui"), 1, "&lCreate a new island...");

        ConfigurationSection section = cfg.getConfigurationSection("creation-gui.schematics");

        for(String schematic : section.getKeys(false)){
            schematicsData.put(schematic + "-permission", section.getString(schematic + ".required-permission"));
            schematicsData.put(schematic + "-bonus", section.getLong(schematic + ".bonus-worth", 0));
            schematicsData.put(schematic + "-biome", section.getString(schematic + ".biome", "PLAINS"));
            schematicsData.put(schematic + "-slot", section.getInt(schematic + ".slot"));
            schematicsData.put(schematic + "-has-access-item",
                    FileUtil.getItemStack(section.getConfigurationSection(schematic + ".has-access-item")));
            schematicsData.put(schematic + "-no-access-item",
                    FileUtil.getItemStack(section.getConfigurationSection(schematic + ".no-access-item")));
        }
    }

    public static IslandCreationMenu createInventory(){
        return new IslandCreationMenu();
    }

}