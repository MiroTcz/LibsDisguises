package me.libraryaddict.disguise.utilities.packets.packethandlers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedAttribute;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import me.libraryaddict.disguise.DisguiseConfig;
import me.libraryaddict.disguise.disguisetypes.*;
import me.libraryaddict.disguise.disguisetypes.watchers.FallingBlockWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.LivingWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.PlayerWatcher;
import me.libraryaddict.disguise.utilities.DisguiseUtilities;
import me.libraryaddict.disguise.utilities.packets.IPacketHandler;
import me.libraryaddict.disguise.utilities.packets.LibsPackets;
import me.libraryaddict.disguise.utilities.packets.PacketsHandler;
import me.libraryaddict.disguise.utilities.reflection.DisguiseValues;
import me.libraryaddict.disguise.utilities.reflection.ReflectionManager;
import org.bukkit.Art;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by libraryaddict on 3/01/2019.
 */
public class PacketHandlerSpawn implements IPacketHandler {
    private PacketsHandler packetsHandler;

    public PacketHandlerSpawn(PacketsHandler packetsHandler) {
        this.packetsHandler = packetsHandler;
    }

    @Override
    public PacketType[] getHandledPackets() {
        return new PacketType[]{PacketType.Play.Server.NAMED_ENTITY_SPAWN, PacketType.Play.Server.SPAWN_ENTITY_LIVING,
                PacketType.Play.Server.SPAWN_ENTITY_EXPERIENCE_ORB, PacketType.Play.Server.SPAWN_ENTITY,
                PacketType.Play.Server.SPAWN_ENTITY_PAINTING};
    }

    @Override
    public void handle(Disguise disguise, PacketContainer sentPacket, LibsPackets packets, Player observer,
            Entity entity) {

        packets.clear();

        constructSpawnPackets(observer, packets, entity);
    }

    /**
     * Construct the packets I need to spawn in the disguise
     */
    private void constructSpawnPackets(final Player observer, LibsPackets packets, Entity disguisedEntity) {
        Disguise disguise = packets.getDisguise();

        if (disguise.getEntity() == null) {
            disguise.setEntity(disguisedEntity);
        }

        // This sends the armor packets so that the player isn't naked.
        // Please note it only sends the packets that wouldn't be sent normally
        if (DisguiseConfig.isEquipmentPacketsEnabled()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack itemstack = disguise.getWatcher().getItemStack(slot);

                if (itemstack == null || itemstack.getType() == Material.AIR) {
                    continue;
                }

                if (disguisedEntity instanceof LivingEntity) {
                    ItemStack item = ReflectionManager.getEquipment(slot, disguisedEntity);

                    if (item != null && item.getType() != Material.AIR) {
                        continue;
                    }
                }

                PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_EQUIPMENT);

                StructureModifier<Object> mods = packet.getModifier();

                mods.write(0, disguisedEntity.getEntityId());
                mods.write(1, ReflectionManager.createEnumItemSlot(slot));
                mods.write(2, ReflectionManager.getNmsItem(itemstack));

                packets.addDelayedPacket(packet);
            }
        }

        if (DisguiseConfig.isMiscDisguisesForLivingEnabled()) {
            if (disguise.getWatcher() instanceof LivingWatcher) {

                ArrayList<WrappedAttribute> attributes = new ArrayList<>();

                WrappedAttribute.Builder builder = WrappedAttribute.newBuilder().attributeKey("generic.maxHealth");

                if (((LivingWatcher) disguise.getWatcher()).isMaxHealthSet()) {
                    builder.baseValue(((LivingWatcher) disguise.getWatcher()).getMaxHealth());
                } else if (DisguiseConfig.isMaxHealthDeterminedByDisguisedEntity() &&
                        disguisedEntity instanceof Damageable) {
                    builder.baseValue(((Damageable) disguisedEntity).getMaxHealth());
                } else {
                    builder.baseValue(DisguiseValues.getDisguiseValues(disguise.getType()).getMaxHealth());
                }

                PacketContainer packet = new PacketContainer(PacketType.Play.Server.UPDATE_ATTRIBUTES);

                builder.packet(packet);

                attributes.add(builder.build());

                packet.getIntegers().write(0, disguisedEntity.getEntityId());
                packet.getAttributeCollectionModifier().write(0, attributes);

                packets.addPacket(packet);
            }
        }

        Location loc = disguisedEntity.getLocation().clone()
                .add(0, DisguiseUtilities.getYModifier(disguisedEntity, disguise), 0);

        byte yaw = (byte) (int) (loc.getYaw() * 256.0F / 360.0F);
        byte pitch = (byte) (int) (loc.getPitch() * 256.0F / 360.0F);

        if (DisguiseConfig.isMovementPacketsEnabled()) {
            yaw = DisguiseUtilities.getYaw(disguise.getType(), disguisedEntity.getType(), yaw);
            pitch = DisguiseUtilities.getPitch(disguise.getType(), disguisedEntity.getType(), pitch);
        }

        if (disguise.getType() == DisguiseType.EXPERIENCE_ORB) {
            PacketContainer spawnOrb = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY_EXPERIENCE_ORB);
            packets.addPacket(spawnOrb);

            StructureModifier<Object> mods = spawnOrb.getModifier();

            mods.write(0, disguisedEntity.getEntityId());
            mods.write(1, loc.getX());
            mods.write(2, loc.getY() + 0.06);
            mods.write(3, loc.getZ());
            mods.write(4, 1);
        } else if (disguise.getType() == DisguiseType.PAINTING) {
            PacketContainer spawnPainting = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY_PAINTING);
            packets.addPacket(spawnPainting);

            StructureModifier<Object> mods = spawnPainting.getModifier();

            mods.write(0, disguisedEntity.getEntityId());
            mods.write(1, disguisedEntity.getUniqueId());
            mods.write(2, ReflectionManager.getBlockPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
            mods.write(3, ReflectionManager.getEnumDirection(((int) loc.getYaw()) % 4));

            int id = ((MiscDisguise) disguise).getData();

            mods.write(4, ReflectionManager.getEnumArt(Art.values()[id]));

            // Make the teleport packet to make it visible..
            PacketContainer teleportPainting = new PacketContainer(PacketType.Play.Server.ENTITY_TELEPORT);
            packets.addPacket(teleportPainting);

            mods = teleportPainting.getModifier();

            mods.write(0, disguisedEntity.getEntityId());
            mods.write(1, loc.getX());
            mods.write(2, loc.getY());
            mods.write(3, loc.getZ());
            mods.write(4, yaw);
            mods.write(5, pitch);
        } else if (disguise.getType().isPlayer()) {
            PlayerDisguise playerDisguise = (PlayerDisguise) disguise;

            String name = playerDisguise.getName();
            WrappedGameProfile gameProfile = playerDisguise.getGameProfile();

            int entityId = disguisedEntity.getEntityId();

            // Send player info along with the disguise
            PacketContainer sendTab = new PacketContainer(PacketType.Play.Server.PLAYER_INFO);

            if (!((PlayerDisguise) disguise).isDisplayedInTab()) {
                // Add player to the list, necessary to spawn them
                sendTab.getModifier().write(0, ReflectionManager.getEnumPlayerInfoAction(0));

                List playerList = Collections
                        .singletonList(ReflectionManager.getPlayerInfoData(sendTab.getHandle(), gameProfile));
                sendTab.getModifier().write(1, playerList);

                packets.addPacket(sendTab);
            }

            // Spawn the player
            PacketContainer spawnPlayer = new PacketContainer(PacketType.Play.Server.NAMED_ENTITY_SPAWN);

            spawnPlayer.getIntegers().write(0, entityId); // Id
            spawnPlayer.getModifier().write(1, gameProfile.getUUID());

            Location spawnAt = disguisedEntity.getLocation();

            boolean selfDisguise = observer == disguisedEntity;

            WrappedDataWatcher newWatcher;

            if (selfDisguise) {
                newWatcher = DisguiseUtilities
                        .createSanitizedDataWatcher(WrappedDataWatcher.getEntityWatcher(disguisedEntity),
                                disguise.getWatcher());
            } else {
                newWatcher = new WrappedDataWatcher();

                spawnAt = observer.getLocation();
                spawnAt.add(spawnAt.getDirection().normalize().multiply(20));
            }

            // Spawn him in front of the observer
            StructureModifier<Double> doubles = spawnPlayer.getDoubles();
            doubles.write(0, spawnAt.getX());
            doubles.write(1, spawnAt.getY());
            doubles.write(2, spawnAt.getZ());

            StructureModifier<Byte> bytes = spawnPlayer.getBytes();
            bytes.write(0, yaw);
            bytes.write(1, pitch);

            spawnPlayer.getDataWatcherModifier().write(0, newWatcher);

            // Make him invisible
            newWatcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(MetaIndex.ENTITY_META.getIndex(),
                    WrappedDataWatcher.Registry.get(Byte.class)), (byte) 32);

            packets.addPacket(spawnPlayer);

            if (DisguiseConfig.isBedPacketsEnabled() && ((PlayerWatcher) disguise.getWatcher()).isSleeping()) {
                PacketContainer[] bedPackets = DisguiseUtilities.getBedPackets(
                        loc.clone().subtract(0, DisguiseUtilities.getYModifier(disguisedEntity, disguise), 0),
                        observer.getLocation(), ((PlayerDisguise) disguise));

                for (PacketContainer packet : bedPackets) {
                    packets.addPacket(packet);
                }
            } else if (!selfDisguise) {
                // Teleport the player back to where he's supposed to be
                PacketContainer teleportPacket = new PacketContainer(PacketType.Play.Server.ENTITY_TELEPORT);

                doubles = teleportPacket.getDoubles();

                teleportPacket.getIntegers().write(0, entityId); // Id
                doubles.write(0, loc.getX());
                doubles.write(1, loc.getY());
                doubles.write(2, loc.getZ());

                bytes = teleportPacket.getBytes();
                bytes.write(0, yaw);
                bytes.write(1, pitch);

                packets.addPacket(teleportPacket);
            }

            if (!selfDisguise) {
                // Send a metadata packet
                PacketContainer metaPacket = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);

                newWatcher = DisguiseUtilities
                        .createSanitizedDataWatcher(WrappedDataWatcher.getEntityWatcher(disguisedEntity),
                                disguise.getWatcher());

                metaPacket.getIntegers().write(0, entityId); // Id
                metaPacket.getWatchableCollectionModifier().write(0, newWatcher.getWatchableObjects());

                packetsHandler.addCancel(disguise, observer);

                // Add a delay to remove the entry from 'cancelMeta'

                packets.addDelayedPacket(metaPacket, 4);
            }

            // Remove player from the list
            PacketContainer deleteTab = sendTab.shallowClone();
            deleteTab.getModifier().write(0, ReflectionManager.getEnumPlayerInfoAction(4));

            if (!((PlayerDisguise) disguise).isDisplayedInTab()) {
                packets.addDelayedPacket(deleteTab, DisguiseConfig.getPlayerDisguisesTablistExpires());
            }
        } else if (disguise.getType().isMob() || disguise.getType() == DisguiseType.ARMOR_STAND) {
            Vector vec = disguisedEntity.getVelocity();

            PacketContainer spawnEntity = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY_LIVING);
            packets.addPacket(spawnEntity);

            StructureModifier<Object> mods = spawnEntity.getModifier();

            mods.write(0, disguisedEntity.getEntityId());
            mods.write(1, disguisedEntity.getUniqueId());
            mods.write(2, disguise.getType().getTypeId());

            // region Vector calculations
            double d1 = 3.9D;
            double d2 = vec.getX();
            double d3 = vec.getY();
            double d4 = vec.getZ();
            if (d2 < -d1)
                d2 = -d1;
            if (d3 < -d1)
                d3 = -d1;
            if (d4 < -d1)
                d4 = -d1;
            if (d2 > d1)
                d2 = d1;
            if (d3 > d1)
                d3 = d1;
            if (d4 > d1)
                d4 = d1;
            // endregion

            mods.write(3, loc.getX());
            mods.write(4, loc.getY());
            mods.write(5, loc.getZ());
            mods.write(6, (int) (d2 * 8000.0D));
            mods.write(7, (int) (d3 * 8000.0D));
            mods.write(8, (int) (d4 * 8000.0D));
            mods.write(9, yaw);
            mods.write(10, pitch);
            mods.write(11, yaw);

            spawnEntity.getDataWatcherModifier().write(0, DisguiseUtilities
                    .createSanitizedDataWatcher(WrappedDataWatcher.getEntityWatcher(disguisedEntity),
                            disguise.getWatcher()));
        } else if (disguise.getType().isMisc()) {
            int objectId = disguise.getType().getObjectId();
            int data = ((MiscDisguise) disguise).getData();

            if (disguise.getType() == DisguiseType.FALLING_BLOCK) {
                ItemStack block = ((FallingBlockWatcher) disguise.getWatcher()).getBlock();

                data = ReflectionManager.getCombinedIdByItemStack(block);
            } else if (disguise.getType() == DisguiseType.FISHING_HOOK && data == -1) {
                // If the MiscDisguise data isn't set. Then no entity id was provided, so default to the owners
                // entity id
                data = observer.getEntityId();
            } else if (disguise.getType() == DisguiseType.ITEM_FRAME) {
                data = ((((int) loc.getYaw() % 360) + 720 + 45) / 90) % 4;
            }

            Object nmsEntity = ReflectionManager.getNmsEntity(disguisedEntity);

            PacketContainer spawnEntity = ProtocolLibrary.getProtocolManager()
                    .createPacketConstructor(PacketType.Play.Server.SPAWN_ENTITY, nmsEntity, objectId, data)
                    .createPacket(nmsEntity, objectId, data);
            packets.addPacket(spawnEntity);

            // If it's not the same type, then highly likely they have different velocity settings which we'd want to
            // cancel
            if (DisguiseType.getType(disguisedEntity) != disguise.getType()) {
                StructureModifier<Integer> ints = spawnEntity.getIntegers();

                ints.write(1, 0);
                ints.write(2, 0);
                ints.write(3, 0);
            }

            spawnEntity.getModifier().write(8, pitch);
            spawnEntity.getModifier().write(9, yaw);

            if (disguise.getType() == DisguiseType.ITEM_FRAME) {
                if (data % 2 == 0) {
                    spawnEntity.getModifier().write(4, loc.getZ() + (data == 0 ? -1 : 1));
                } else {
                    spawnEntity.getModifier().write(2, loc.getX() + (data == 3 ? -1 : 1));
                }
            }
        }

        if (packets.getPackets().size() <= 1 || disguise.isPlayerDisguise()) {
            PacketContainer rotateHead = new PacketContainer(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
            packets.addPacket(rotateHead);

            StructureModifier<Object> mods = rotateHead.getModifier();

            mods.write(0, disguisedEntity.getEntityId());
            mods.write(1, yaw);
        }

        if (disguise.getType() == DisguiseType.EVOKER_FANGS) {
            PacketContainer newPacket = new PacketContainer(PacketType.Play.Server.ENTITY_STATUS);

            StructureModifier<Object> mods = newPacket.getModifier();
            mods.write(0, disguise.getEntity().getEntityId());
            mods.write(1, (byte) 4);

            packets.addPacket(newPacket);
        }
    }
}
