package com.bgsoftware.superiorskyblock.database.serialization;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.data.DatabaseBridge;
import com.bgsoftware.superiorskyblock.api.enums.Rating;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandFlag;
import com.bgsoftware.superiorskyblock.api.island.IslandPrivilege;
import com.bgsoftware.superiorskyblock.api.island.PlayerRole;
import com.bgsoftware.superiorskyblock.api.missions.Mission;
import com.bgsoftware.superiorskyblock.api.objects.Pair;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.database.DatabaseResult;
import com.bgsoftware.superiorskyblock.database.cache.CachedIslandInfo;
import com.bgsoftware.superiorskyblock.database.cache.CachedWarpCategoryInfo;
import com.bgsoftware.superiorskyblock.database.cache.CachedWarpInfo;
import com.bgsoftware.superiorskyblock.database.cache.DatabaseCache;
import com.bgsoftware.superiorskyblock.database.loader.v1.deserializer.IDeserializer;
import com.bgsoftware.superiorskyblock.database.loader.v1.deserializer.JsonDeserializer;
import com.bgsoftware.superiorskyblock.database.loader.v1.deserializer.MultipleDeserializer;
import com.bgsoftware.superiorskyblock.database.loader.v1.deserializer.RawDeserializer;
import com.bgsoftware.superiorskyblock.island.SPlayerRole;
import com.bgsoftware.superiorskyblock.island.bank.SBankTransaction;
import com.bgsoftware.superiorskyblock.island.permissions.PlayerPermissionNode;
import com.bgsoftware.superiorskyblock.key.Key;
import com.bgsoftware.superiorskyblock.key.dataset.KeyMap;
import com.bgsoftware.superiorskyblock.module.BuiltinModules;
import com.bgsoftware.superiorskyblock.upgrade.UpgradeValue;
import com.bgsoftware.superiorskyblock.utils.LocationUtils;
import com.bgsoftware.superiorskyblock.utils.StringUtils;
import com.bgsoftware.superiorskyblock.utils.islands.IslandUtils;
import com.bgsoftware.superiorskyblock.utils.items.ItemUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;

public final class IslandsDeserializer {

    private static final SuperiorSkyblockPlugin plugin = SuperiorSkyblockPlugin.getPlugin();
    private static final Gson gson = new GsonBuilder().create();
    private static final IDeserializer oldDataDeserializer = new MultipleDeserializer(
            new JsonDeserializer(null), new RawDeserializer(null, plugin)
    );

    private IslandsDeserializer() {

    }

    public static void deserializeMembers(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_members", membersRow -> {
            DatabaseResult members = new DatabaseResult(membersRow);

            Optional<UUID> uuid = members.getUUID("island");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load island members for null islands, skipping...");
                return;
            }

            Optional<UUID> playerUUID = members.getUUID("player");
            if (!playerUUID.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load island members with invalid uuids for %s, skipping...", uuid.get()));
                return;
            }

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);

            PlayerRole playerRole = members.getInt("role").map(SPlayerRole::fromId)
                    .orElse(SPlayerRole.defaultRole());

            SuperiorPlayer superiorPlayer = plugin.getPlayers().getSuperiorPlayer(playerUUID.get());
            superiorPlayer.setPlayerRole(playerRole);

            cachedIslandInfo.members.add(superiorPlayer);
        });
    }

    public static void deserializeBanned(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_bans", bansRow -> {
            DatabaseResult bans = new DatabaseResult(bansRow);

            Optional<UUID> uuid = bans.getUUID("island");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load banned players for null islands, skipping...");
                return;
            }

            Optional<UUID> playerUUID = bans.getUUID("player");
            if (!playerUUID.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load banned players with invalid uuids for %s, skipping...", uuid.get()));
                return;
            }

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);
            SuperiorPlayer superiorPlayer = plugin.getPlayers().getSuperiorPlayer(playerUUID.get());
            cachedIslandInfo.bannedPlayers.add(superiorPlayer);
        });
    }

    public static void deserializeVisitors(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_visitors", visitorsRow -> {
            DatabaseResult visitors = new DatabaseResult(visitorsRow);

            Optional<UUID> islandUUID = visitors.getUUID("island");
            if (!islandUUID.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load island visitors for null islands, skipping...");
                return;
            }

            Optional<UUID> uuid = visitors.getUUID("player");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load island visitors with invalid uuids for %s, skipping...", islandUUID.get()));
                return;
            }

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(islandUUID.get(), CachedIslandInfo::new);
            SuperiorPlayer visitorPlayer = plugin.getPlayers().getSuperiorPlayer(uuid.get());
            long visitTime = visitors.getLong("visit_time").orElse(System.currentTimeMillis());
            cachedIslandInfo.uniqueVisitors.add(new Pair<>(visitorPlayer, visitTime));
        });
    }

    public static void deserializePlayerPermissions(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_player_permissions", playerPermissionRow -> {
            DatabaseResult playerPermissions = new DatabaseResult(playerPermissionRow);

            Optional<UUID> uuid = playerPermissions.getUUID("island");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load player permissions for null islands, skipping...");
                return;
            }

            Optional<UUID> playerUUID = playerPermissions.getUUID("player");
            if (!playerUUID.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load player permissions for invalid players on %s, skipping...", uuid.get()));
                return;
            }

            Optional<IslandPrivilege> islandPrivilege = playerPermissions.getString("permission").map(name -> {
                try {
                    return IslandPrivilege.getByName(name);
                } catch (NullPointerException error) {
                    return null;
                }
            });
            if (!islandPrivilege.isPresent()) {
                SuperiorSkyblockPlugin.log(String.format("&cCannot load player permissions with invalid permission " +
                        "for player %s, skipping...", playerUUID.get()));
                return;
            }

            Optional<Byte> status = playerPermissions.getByte("status");
            if (!status.isPresent()) {
                SuperiorSkyblockPlugin.log(String.format("&cCannot load player permissions with invalid status " +
                        "for player %s, skipping...", playerUUID.get()));
                return;
            }

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);
            SuperiorPlayer superiorPlayer = plugin.getPlayers().getSuperiorPlayer(playerUUID.get());
            PlayerPermissionNode permissionNode = cachedIslandInfo.playerPermissions.computeIfAbsent(superiorPlayer,
                    s -> new PlayerPermissionNode(superiorPlayer, null));
            permissionNode.loadPrivilege(islandPrivilege.get(), status.get());
        });
    }

    public static void deserializeRolePermissions(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_role_permissions", rolePermissionsRow -> {
            DatabaseResult rolePermissions = new DatabaseResult(rolePermissionsRow);

            Optional<UUID> uuid = rolePermissions.getUUID("island");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load role permissions for null islands, skipping...");
                return;
            }

            Optional<PlayerRole> playerRole = rolePermissions.getInt("role").map(SPlayerRole::fromId);
            if (!playerRole.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load role permissions with invalid role for %s, skipping...", uuid.get()));
                return;
            }

            Optional<IslandPrivilege> islandPrivilege = rolePermissions.getString("permission").map(name -> {
                try {
                    return IslandPrivilege.getByName(name);
                } catch (NullPointerException error) {
                    return null;
                }
            });
            if (!islandPrivilege.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load role permissions with invalid permission for %s, skipping...", uuid.get()));
                return;
            }

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);
            cachedIslandInfo.rolePermissions.put(islandPrivilege.get(), playerRole.get());
        });
    }

    public static void deserializeUpgrades(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_upgrades", upgradesRow -> {
            DatabaseResult upgrades = new DatabaseResult(upgradesRow);

            Optional<UUID> uuid = upgrades.getUUID("island");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load upgrades for null islands, skipping...");
                return;
            }

            Optional<String> upgrade = upgrades.getString("upgrade");
            if (!upgrade.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load upgrades with invalid upgrade names for %s, skipping...", uuid.get()));
                return;
            }

            Optional<Integer> level = upgrades.getInt("level");
            if (!level.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load upgrades with invalid levels for %s, skipping...", uuid.get()));
                return;
            }

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);
            cachedIslandInfo.upgrades.put(upgrade.get(), level.get());
        });
    }

    public static void deserializeWarps(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_warps", islandWarpsRow -> {
            DatabaseResult islandWarp = new DatabaseResult(islandWarpsRow);

            Optional<UUID> uuid = islandWarp.getUUID("island");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load warps for null islands, skipping...");
                return;
            }

            Optional<String> name = islandWarp.getString("name").map(IslandUtils::getWarpName).map(_name -> {
                return IslandUtils.isWarpNameLengthValid(_name) ? _name : _name.substring(0, IslandUtils.getMaxWarpNameLength());
            });
            if (!name.isPresent() || name.get().isEmpty()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load warps with invalid names for %s, skipping...", uuid.get()));
                return;
            }

            Optional<Location> location = islandWarp.getString("location").map(LocationUtils::getLocation);
            if (!location.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load warps with invalid locations for %s, skipping...", uuid.get()));
                return;
            }

            CachedWarpInfo cachedWarpInfo = new CachedWarpInfo();
            cachedWarpInfo.name = name.get();
            cachedWarpInfo.category = islandWarp.getString("category").orElse("");
            cachedWarpInfo.location = location.get();
            cachedWarpInfo.isPrivate = islandWarp.getBoolean("private").orElse(true);
            cachedWarpInfo.icon = islandWarp.getString("icon").map(ItemUtils::deserializeItem).orElse(null);

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);
            cachedIslandInfo.cachedWarpInfoList.add(cachedWarpInfo);
        });
    }

    public static void deserializeBlockCounts(String blocks, Island island) {
        if (blocks == null || blocks.isEmpty())
            return;

        JsonArray blockCounts;

        try {
            blockCounts = gson.fromJson(blocks, JsonArray.class);
        } catch (JsonSyntaxException error) {
            blockCounts = gson.fromJson(oldDataDeserializer.deserializeBlockCounts(blocks), JsonArray.class);
        }

        blockCounts.forEach(blockCountElement -> {
            JsonObject blockCountObject = blockCountElement.getAsJsonObject();
            Key blockKey = Key.of(blockCountObject.get("id").getAsString());
            BigInteger amount = new BigInteger(blockCountObject.get("amount").getAsString());
            island.handleBlockPlace(blockKey, amount, false, false);
        });
    }

    public static void deserializeBlockLimits(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_block_limits", blockLimitRow -> {
            DatabaseResult blockLimits = new DatabaseResult(blockLimitRow);

            Optional<UUID> uuid = blockLimits.getUUID("island");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load block limits for null islands, skipping...");
                return;
            }

            Optional<Key> block = blockLimits.getString("block").map(Key::of);
            if (!block.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load block limits for invalid blocks for %s, skipping...", uuid.get()));
                return;
            }

            Optional<Integer> limit = blockLimits.getInt("limit");
            if (!limit.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load block limits with invalid limits for %s, skipping...", uuid.get()));
                return;
            }

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);
            cachedIslandInfo.blockLimits.put(block.get(), new UpgradeValue<>(limit.get(), i -> i < 0));
        });
    }

    public static void deserializeEntityLimits(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_entity_limits", entityLimitsRow -> {
            DatabaseResult entityLimits = new DatabaseResult(entityLimitsRow);

            Optional<UUID> uuid = entityLimits.getUUID("island");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load entity limits for null islands, skipping...");
                return;
            }

            Optional<Key> entity = entityLimits.getString("entity").map(Key::of);
            if (!entity.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load entity limits for invalid entities on %s, skipping...", uuid.get()));
                return;
            }

            Optional<Integer> limit = entityLimits.getInt("limit");
            if (!limit.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load entity limits with invalid limits for %s, skipping...", uuid.get()));
                return;
            }

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);
            cachedIslandInfo.entityLimits.put(entity.get(), new UpgradeValue<>(limit.get(), i -> i < 0));
        });
    }

    public static void deserializeRatings(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_ratings", ratingsRow -> {
            DatabaseResult ratings = new DatabaseResult(ratingsRow);

            Optional<UUID> islandUUID = ratings.getUUID("island");
            if (!islandUUID.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load ratings for null islands, skipping...");
                return;
            }

            Optional<UUID> uuid = ratings.getUUID("player");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load ratings with invalid players for %s, skipping...", uuid.get()));
                return;
            }

            Optional<Rating> rating = ratings.getInt("rating").map(value -> {
                try {
                    return Rating.valueOf(value);
                } catch (ArrayIndexOutOfBoundsException error) {
                    return null;
                }
            });
            if (!rating.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load ratings with invalid rating value for %s, skipping...", uuid.get()));
                return;
            }

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(islandUUID.get(), CachedIslandInfo::new);
            cachedIslandInfo.ratings.put(uuid.get(), rating.get());
        });
    }

    public static void deserializeMissions(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_missions", missionsRow -> {
            DatabaseResult missions = new DatabaseResult(missionsRow);

            Optional<UUID> uuid = missions.getUUID("island");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load island missions for null islands, skipping...");
                return;
            }

            Optional<Mission<?>> mission = missions.getString("name").map(plugin.getMissions()::getMission);
            if (!mission.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load island missions with invalid missions for %s, skipping...", uuid.get()));
                return;
            }

            Optional<Integer> finishCount = missions.getInt("finish_count");
            if (!finishCount.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load island missions with invalid finish count for %s, skipping...", uuid.get()));
                return;
            }

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);
            cachedIslandInfo.completedMissions.put(mission.get(), finishCount.get());
        });
    }

    public static void deserializeIslandFlags(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_flags", islandFlagRow -> {
            DatabaseResult islandFlagResult = new DatabaseResult(islandFlagRow);

            Optional<UUID> uuid = islandFlagResult.getUUID("island");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load island flags for null islands, skipping...");
                return;
            }

            Optional<IslandFlag> islandFlag = islandFlagResult.getString("name").map(name -> {
                try {
                    return IslandFlag.getByName(name);
                } catch (NullPointerException error) {
                    return null;
                }
            });
            if (!islandFlag.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load island flags with invalid flags for %s, skipping...", uuid.get()));
                return;
            }

            Optional<Byte> status = islandFlagResult.getByte("status");
            if (!status.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load island flags with invalid status for %s, skipping...", uuid.get()));
                return;
            }

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);
            cachedIslandInfo.islandFlags.put(islandFlag.get(), status.get());
        });
    }

    public static void deserializeGenerators(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_generators", generatorsRow -> {
            DatabaseResult generators = new DatabaseResult(generatorsRow);

            Optional<UUID> uuid = generators.getUUID("island");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load generator rates for null islands, skipping...");
                return;
            }

            Optional<Integer> environment = generators.getEnum("environment", World.Environment.class)
                    .map(Enum::ordinal);
            if (!environment.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load generator rates with invalid environment for %s, skipping...", uuid.get()));
                return;
            }

            Optional<Key> block = generators.getString("block").map(Key::of);
            if (!block.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load generator rates with invalid block for %s, skipping...", uuid.get()));
                return;
            }

            Optional<Integer> rate = generators.getInt("rate");
            if (!rate.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load generator rates with invalid rate for %s, skipping...", uuid.get()));
                return;
            }

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);
            (cachedIslandInfo.cobbleGeneratorValues[environment.get()] = new KeyMap<>())
                    .put(block.get(), new UpgradeValue<>(rate.get(), n -> n < 0));
        });
    }

    public static void deserializeIslandHomes(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_homes", islandHomesRow -> {
            DatabaseResult islandHomes = new DatabaseResult(islandHomesRow);

            Optional<UUID> uuid = islandHomes.getUUID("island");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load island homes for null islands, skipping...");
                return;
            }

            Optional<Integer> environment = islandHomes.getEnum("environment", World.Environment.class)
                    .map(Enum::ordinal);
            if (!environment.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load island homes with invalid environment for %s, skipping...", uuid.get()));
                return;
            }

            Optional<Location> location = islandHomes.getString("location").map(LocationUtils::getLocation);
            if (!location.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load island homes with invalid location for %s, skipping...", uuid.get()));
                return;
            }

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);
            cachedIslandInfo.islandHomes[environment.get()] = location.get();
        });
    }

    public static void deserializeVisitorHomes(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_visitor_homes", islandVisitorHomesRow -> {
            DatabaseResult islandVisitorHomes = new DatabaseResult(islandVisitorHomesRow);

            Optional<UUID> uuid = islandVisitorHomes.getUUID("island");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load island homes for null islands, skipping...");
                return;
            }

            Optional<Integer> environment = islandVisitorHomes.getEnum("environment", World.Environment.class)
                    .map(Enum::ordinal);
            if (!environment.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load island homes with invalid environment for %s, skipping...", uuid.get()));
                return;
            }

            Optional<Location> location = islandVisitorHomes.getString("location").map(LocationUtils::getLocation);
            if (!location.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load island homes with invalid location for %s, skipping...", uuid.get()));
                return;
            }

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);
            cachedIslandInfo.visitorHomes[environment.get()] = location.get();
        });
    }

    public static void deserializeEffects(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_effects", islandEffectRow -> {
            DatabaseResult islandEffects = new DatabaseResult(islandEffectRow);

            Optional<UUID> uuid = islandEffects.getUUID("island");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load island effects for null islands, skipping...");
                return;
            }

            Optional<PotionEffectType> effectType = islandEffects.getString("effect_type")
                    .map(PotionEffectType::getByName);
            if (!effectType.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load island effects with invalid effect for %s, skipping...", uuid.get()));
                return;
            }

            Optional<Integer> level = islandEffects.getInt("level");
            if (!level.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load island effects with invalid level for %s, skipping...", uuid.get()));
                return;
            }

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);
            cachedIslandInfo.islandEffects.put(effectType.get(), new UpgradeValue<>(level.get(), i -> i < 0));
        });
    }

    public static void deserializeIslandChest(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_chests", islandChestsRow -> {
            DatabaseResult islandChests = new DatabaseResult(islandChestsRow);

            Optional<UUID> uuid = islandChests.getUUID("island");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load island chests for null islands, skipping...");
                return;
            }

            Optional<Integer> index = islandChests.getInt("index");
            if (!index.isPresent() || index.get() < 0) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load island chest with invalid index for %s, skipping...", uuid.get()));
                return;
            }

            Optional<ItemStack[]> contents = islandChests.getString("contents").map(ItemUtils::deserialize);
            if (!contents.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load island chest with invalid contents for %s, skipping...", uuid.get()));
                return;
            }

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);

            while (index.get() > cachedIslandInfo.islandChests.size()) {
                cachedIslandInfo.islandChests.add(new ItemStack[plugin.getSettings().getIslandChests().getDefaultSize() * 9]);
            }

            cachedIslandInfo.islandChests.add(contents.get());
        });
    }

    public static void deserializeRoleLimits(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_role_limits", roleLimitRaw -> {
            DatabaseResult roleLimits = new DatabaseResult(roleLimitRaw);

            Optional<UUID> uuid = roleLimits.getUUID("island");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load role limits for null islands, skipping...");
                return;
            }

            Optional<PlayerRole> playerRole = roleLimits.getInt("role").map(SPlayerRole::fromId);
            if (!playerRole.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load role limit for invalid role on %s, skipping...", uuid.get()));
                return;
            }

            Optional<Integer> limit = roleLimits.getInt("limit");
            if (!limit.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load role limit for invalid limit on %s, skipping...", uuid.get()));
                return;
            }

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);
            cachedIslandInfo.roleLimits.put(playerRole.get(), new UpgradeValue<>(limit.get(), i -> i < 0));
        });
    }

    public static void deserializeWarpCategories(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_warp_categories", warpCategoryRow -> {
            DatabaseResult warpCategory = new DatabaseResult(warpCategoryRow);

            Optional<UUID> uuid = warpCategory.getUUID("island");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load warp categories for null islands, skipping...");
                return;
            }

            Optional<String> name = warpCategory.getString("name").map(StringUtils::stripColors);
            if (!name.isPresent() || name.get().isEmpty()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load warp categories with invalid name for %s, skipping...", uuid.get()));
                return;
            }

            CachedWarpCategoryInfo cachedWarpCategoryInfo = new CachedWarpCategoryInfo();
            cachedWarpCategoryInfo.name = name.get();
            cachedWarpCategoryInfo.slot = warpCategory.getInt("slot").orElse(-1);
            cachedWarpCategoryInfo.icon = warpCategory.getString("icon").map(ItemUtils::deserializeItem).orElse(null);

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);
            cachedIslandInfo.cachedWarpCategoryInfoList.add(cachedWarpCategoryInfo);
        });
    }

    public static void deserializeIslandBank(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_banks", islandBankRow -> {
            DatabaseResult islandBank = new DatabaseResult(islandBankRow);

            Optional<UUID> uuid = islandBank.getUUID("island");
            if (!uuid.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load island banks for null islands, skipping...");
                return;
            }

            Optional<BigDecimal> balance = islandBank.getBigDecimal("balance");
            if (!balance.isPresent()) {
                SuperiorSkyblockPlugin.log(
                        String.format("&cCannot load island banks with invalid balance for %s, skipping...", uuid.get()));
                return;
            }

            long currentTime = System.currentTimeMillis() / 1000;

            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);
            cachedIslandInfo.balance = balance.get();
            cachedIslandInfo.lastInterestTime = islandBank.getLong("last_interest_time").orElse(currentTime);

            if (cachedIslandInfo.lastInterestTime > currentTime)
                cachedIslandInfo.lastInterestTime /= 1000L;
        });
    }

    public static void deserializeIslandSettings(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        databaseBridge.loadAllObjects("islands_settings", islandSettingsRow -> {
            DatabaseResult islandSettings = new DatabaseResult(islandSettingsRow);

            Optional<String> island = islandSettings.getString("island");
            if (!island.isPresent()) {
                SuperiorSkyblockPlugin.log("&cCannot load island settings of null island, skipping ");
                return;
            }

            UUID uuid = UUID.fromString(island.get());
            CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid, CachedIslandInfo::new);

            cachedIslandInfo.islandSize = new UpgradeValue<>(islandSettings.getInt("size")
                    .orElse(-1), i -> i < 0);
            cachedIslandInfo.teamLimit = new UpgradeValue<>(islandSettings.getInt("members_limit")
                    .orElse(-1), i -> i < 0);
            cachedIslandInfo.warpsLimit = new UpgradeValue<>(islandSettings.getInt("warps_limit")
                    .orElse(-1), i -> i < 0);
            cachedIslandInfo.cropGrowth = new UpgradeValue<>(islandSettings.getDouble("crop_growth_multiplier")
                    .orElse(-1D), i -> i < 0);
            cachedIslandInfo.spawnerRates = new UpgradeValue<>(islandSettings.getDouble("spawner_rates_multiplier")
                    .orElse(-1D), i -> i < 0);
            cachedIslandInfo.mobDrops = new UpgradeValue<>(islandSettings.getDouble("mob_drops_multiplier")
                    .orElse(-1D), i -> i < 0);
            cachedIslandInfo.coopLimit = new UpgradeValue<>(islandSettings.getInt("coops_limit")
                    .orElse(-1), i -> i < 0);
            cachedIslandInfo.bankLimit = new UpgradeValue<>(islandSettings.getBigDecimal("bank_limit")
                    .orElse(new BigDecimal(-2)), i -> i.compareTo(new BigDecimal(-1)) < 0);
        });
    }

    public static void deserializeBankTransactions(DatabaseBridge databaseBridge, DatabaseCache<CachedIslandInfo> databaseCache) {
        if (BuiltinModules.BANK.bankLogs && BuiltinModules.BANK.cacheAllLogs) {
            databaseBridge.loadAllObjects("bank_transactions", bankTransactionRow -> {
                DatabaseResult bankTransaction = new DatabaseResult(bankTransactionRow);

                Optional<UUID> uuid = bankTransaction.getUUID("island");
                if (!uuid.isPresent()) {
                    SuperiorSkyblockPlugin.log("&cCannot load bank transaction for null islands, skipping...");
                    return;
                }

                CachedIslandInfo cachedIslandInfo = databaseCache.computeIfAbsentInfo(uuid.get(), CachedIslandInfo::new);
                SBankTransaction.fromDatabase(bankTransaction).ifPresent(cachedIslandInfo.bankTransactions::add);
            });
        }
    }

}
