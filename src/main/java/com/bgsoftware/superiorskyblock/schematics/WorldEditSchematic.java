package com.bgsoftware.superiorskyblock.schematics;

import com.bgsoftware.superiorskyblock.api.events.IslandSchematicPasteEvent;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.key.Key;
import com.bgsoftware.superiorskyblock.api.schematic.Schematic;
import com.bgsoftware.superiorskyblock.island.SIsland;
import com.boydti.fawe.object.clipboard.FaweClipboard;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;

import java.lang.reflect.Method;

@SuppressWarnings("JavaReflectionMemberAccess")
public final class WorldEditSchematic extends BaseSchematic implements Schematic {

    private static Method blockVector3AtMethod = null;
    private static Method blockVector3PasteMethod = null;

    private static Method baseBlockId = null;
    private static Method baseBlockData = null;
    private static Method getBlockTypes = null;
    private static Method getBlockData = null;
    private static Method adaptBlockType = null;

    static {
        try{
            Class<?> blockVector3Class = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            blockVector3AtMethod = blockVector3Class.getMethod("at", int.class, int.class, int.class);
            //noinspection JavaReflectionMemberAccess
            blockVector3PasteMethod = com.boydti.fawe.object.schematic.Schematic.class
                    .getMethod("paste", World.class, blockVector3Class, boolean.class, boolean.class, Transform.class);
        }catch(Throwable ignored){ }

        try{
            Class<?> blockTypesClass = Class.forName("com.sk89q.worldedit.world.block.BlockTypes");
            getBlockTypes = BaseBlock.class.getMethod("getBlockType");
            getBlockData = BaseBlock.class.getMethod("getInternalId");
            adaptBlockType = BukkitAdapter.class.getMethod("adapt", blockTypesClass);
        }catch(Throwable ignored){}

        try{
            baseBlockId = BaseBlock.class.getMethod("getId");
            baseBlockData = BaseBlock.class.getMethod("getData");
        }catch(Throwable ignored){ }
    }

    private com.boydti.fawe.object.schematic.Schematic schematic;


    public WorldEditSchematic(String name, com.boydti.fawe.object.schematic.Schematic schematic){
        super(name);
        this.schematic = schematic;
        readBlocks();
    }

    @Override
    public void pasteSchematic(Location location, Runnable callback) {
        pasteSchematic(null, location, callback);
    }

    @Override
    public void pasteSchematic(Island island, Location location, Runnable callback) {
        if(schematicProgress) {
            pasteSchematicQueue.push(new PasteSchematicData(this, island, location, callback));
            return;
        }

        schematicProgress = true;

        EditSession editSession;

        try{
            Object point = blockVector3AtMethod.invoke(null, location.getBlockX(), location.getBlockY(), location.getBlockZ());
            editSession = (EditSession) blockVector3PasteMethod.invoke(schematic, new BukkitWorld(location.getWorld()), point, false, true, null);
        }catch(Throwable ex){
            com.sk89q.worldedit.Vector point = new com.sk89q.worldedit.Vector(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            editSession = schematic.paste(new BukkitWorld(location.getWorld()), point, true, true, null);
        }

        editSession.addNotifyTask(() -> {
            ((SIsland) island).handleBlocksPlace(cachedCounts);

            IslandSchematicPasteEvent islandSchematicPasteEvent = new IslandSchematicPasteEvent(island, name, location);
            Bukkit.getPluginManager().callEvent(islandSchematicPasteEvent);

            callback.run();

            schematicProgress = false;

            if(pasteSchematicQueue.size() != 0){
                PasteSchematicData data = pasteSchematicQueue.pop();
                data.schematic.pasteSchematic(data.island, data.location, data.callback);
            }
        });
    }

    private void readBlocks() {
        BlockArrayClipboard clipboard = (BlockArrayClipboard) schematic.getClipboard();

        assert clipboard != null;

        try {
            clipboard.IMP.forEach(new BlockReader() {
                @Override
                public void run(int x, int y, int z, BaseBlock block) {
                    readBlock(block);
                }
            }, false);
        }catch(Throwable ex){
            clipboard.IMP.forEach(new FaweClipboard.BlockReader() {
                @Override
                public void run(int x, int y, int z, BlockState block) {
                    readBlock(block);
                }
            }, false);
        }
    }

    private void readBlock(Object baseBlock){
        Key key;
        try {
            Material material = (Material) adaptBlockType.invoke(null, getBlockTypes.invoke(baseBlock));
            int data = (Integer) getBlockData.invoke(baseBlock);
            key = Key.of(material, (byte) data);
        }catch(Throwable ignored){
            try{
                int id = (Integer) baseBlockId.invoke(baseBlock);
                int data = (Integer) baseBlockData.invoke(baseBlock);
                //noinspection deprecation
                key = Key.of(Material.getMaterial(id), (byte) data);
            }catch(Throwable ex){
                ex.printStackTrace();
                return;
            }
        }
        cachedCounts.put(key, cachedCounts.getRaw(key, 0) + 1);
    }

    private static abstract class BlockReader extends FaweClipboard.BlockReader{

        public void run(int x, int y, int z, BaseBlock block){

        }

        public void run(int x, int y, int z, BlockState block){

        }

    }

}
