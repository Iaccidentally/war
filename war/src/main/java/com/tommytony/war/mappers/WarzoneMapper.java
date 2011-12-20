package com.tommytony.war.mappers;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import bukkit.tommytony.war.War;

import com.tommytony.war.FlagReturn;
import com.tommytony.war.Monument;
import com.tommytony.war.Team;
import com.tommytony.war.TeamKind;
import com.tommytony.war.TeamSpawnStyle;
import com.tommytony.war.Warzone;
import com.tommytony.war.ZoneLobby;
import com.tommytony.war.volumes.Volume;
import com.tommytony.war.volumes.ZoneVolume;

/**
 *
 * @author tommytony
 *
 */
public class WarzoneMapper {

	public static Warzone load(String name, boolean createNewVolume) {
		// war.getLogger().info("Loading warzone " + name + " config and blocks...");
		PropertiesFile warzoneConfig = new PropertiesFile(War.war.getDataFolder().getPath() + "/warzone-" + name + ".txt");
		try {
			warzoneConfig.load();
		} catch (IOException e) {
			War.war.getLogger().info("Failed to load warzone-" + name + ".txt file.");
			e.printStackTrace();
		}

		// world
		String worldStr = warzoneConfig.getProperty("world");
		World world = null;
		if (worldStr == null || worldStr.equals("")) {
			world = War.war.getServer().getWorlds().get(0); // default to first world
		} else {
			world = War.war.getServer().getWorld(worldStr);
		}

		if (world == null) {
			War.war.log("Failed to restore warzone " + name + ". The specified world (name: " + worldStr + ") does not exist!", Level.WARNING);
		} else {
			// Create the zone
			Warzone warzone = new Warzone(world, name);

			// Create file if needed
			if (!warzoneConfig.containsKey("name")) {
				WarzoneMapper.save(warzone, false);
				War.war.getLogger().info("Warzone " + name + " config file created.");
				try {
					warzoneConfig.load();
				} catch (IOException e) {
					// war.getLogger().info("Failed to reload warzone-" + name + ".txt file after creating it.");
					e.printStackTrace();
				}
			}

			// teleport
			String teleportStr = warzoneConfig.getString("teleport");
			if (teleportStr != null && !teleportStr.equals("")) {
				String[] teleportSplit = teleportStr.split(",");
				int teleX = Integer.parseInt(teleportSplit[0]);
				int teleY = Integer.parseInt(teleportSplit[1]);
				int teleZ = Integer.parseInt(teleportSplit[2]);
				int yaw = Integer.parseInt(teleportSplit[3]);
				warzone.setTeleport(new Location(world, teleX, teleY, teleZ, yaw, 0));
			}

			// teams
			String teamsStr = warzoneConfig.getString("teams");
			if (teamsStr != null && !teamsStr.equals("")) {
				String[] teamsSplit = teamsStr.split(";");
				warzone.getTeams().clear();
				for (String teamStr : teamsSplit) {
					if (teamStr != null && !teamStr.equals("")) {
						String[] teamStrSplit = teamStr.split(",");
						int teamX = Integer.parseInt(teamStrSplit[1]);
						int teamY = Integer.parseInt(teamStrSplit[2]);
						int teamZ = Integer.parseInt(teamStrSplit[3]);
						Location teamLocation = new Location(world, teamX, teamY, teamZ);
						if (teamStrSplit.length > 4) {
							int yaw = Integer.parseInt(teamStrSplit[4]);
							teamLocation.setYaw(yaw);
						}
						Team team = new Team(teamStrSplit[0], TeamKind.teamKindFromString(teamStrSplit[0]), teamLocation, warzone);
						team.setRemainingLives(warzone.getLifePool());
						warzone.getTeams().add(team);
					}
				}
			}

			// teamFlags
			String teamFlagsStr = warzoneConfig.getString("teamFlags");
			if (teamFlagsStr != null && !teamFlagsStr.equals("")) {
				String[] teamFlagsSplit = teamFlagsStr.split(";");
				for (String teamFlagStr : teamFlagsSplit) {
					if (teamFlagStr != null && !teamFlagStr.equals("")) {
						String[] teamFlagStrSplit = teamFlagStr.split(",");
						Team team = warzone.getTeamByKind(TeamKind.teamKindFromString(teamFlagStrSplit[0]));
						if (team != null) {
							int teamFlagX = Integer.parseInt(teamFlagStrSplit[1]);
							int teamFlagY = Integer.parseInt(teamFlagStrSplit[2]);
							int teamFlagZ = Integer.parseInt(teamFlagStrSplit[3]);
							Location teamFlagLocation = new Location(world, teamFlagX, teamFlagY, teamFlagZ);
							if (teamFlagStrSplit.length > 4) {
								int yaw = Integer.parseInt(teamFlagStrSplit[4]);
								teamFlagLocation.setYaw(yaw);
							}
							team.setTeamFlag(teamFlagLocation); // this may screw things up
						}
					}
				}
			}

			// ff
			if (warzoneConfig.containsKey("friendlyFire")) {
				warzone.setFriendlyFire(warzoneConfig.getBoolean("friendlyFire"));
			}

			// loadout
			String loadoutStr = warzoneConfig.getString("loadout");
			if (loadoutStr != null && !loadoutStr.equals("")) {
				fromStringToLoadout(loadoutStr, warzone.getLoadout());
			}
			
			// extraLoadouts
			String extraLoadoutStr = warzoneConfig.getString("extraLoadouts");
			String[] extraLoadoutsSplit = extraLoadoutStr.split(",");
			War.war.getDefaultExtraLoadouts().clear();
			for (String nameStr : extraLoadoutsSplit) {
				if (nameStr != null && !nameStr.equals("")) {
					warzone.getExtraLoadouts().put(nameStr, new HashMap<Integer, ItemStack>());
				}
			}
			
			for (String extraName : warzone.getExtraLoadouts().keySet()) {
				String loadoutString = warzoneConfig.getString(extraName + "Loadout");
				HashMap<Integer, ItemStack> loadout = warzone.getExtraLoadouts().get(extraName);
				fromStringToLoadout(loadoutString, loadout);
			}

			// authors
			if (warzoneConfig.containsKey("author") && !warzoneConfig.getString("author").equals("")) {
				for(String authorStr : warzoneConfig.getString("author").split(",")) {
					if (!authorStr.equals("")) {
						warzone.addAuthor(authorStr);
					}
				}
			}

			// life pool (always set after teams, so the teams' remaining lives get initialized properly by this setter)
			if (warzoneConfig.containsKey("lifePool")) {
				warzone.setLifePool(warzoneConfig.getInt("lifePool"));
			}

			// monument heal
			if (warzoneConfig.containsKey("monumentHeal")) {
				warzone.setMonumentHeal(warzoneConfig.getInt("monumentHeal"));
			}

			// autoAssignOnly
			if (warzoneConfig.containsKey("autoAssignOnly")) {
				warzone.setAutoAssignOnlyWithoutResettingLobby(warzoneConfig.getBoolean("autoAssignOnly"));
			}

			// flagPointsOnly
			if (warzoneConfig.containsKey("flagPointsOnly")) {
				warzone.setFlagPointsOnly(warzoneConfig.getBoolean("flagPointsOnly"));
			}

			// team cap
			if (warzoneConfig.containsKey("teamCap")) {
				warzone.setTeamCap(warzoneConfig.getInt("teamCap"));
			}
			
			// score cap
			if (warzoneConfig.containsKey("scoreCap")) {
				warzone.setScoreCap(warzoneConfig.getInt("scoreCap"));
			}

			// blockHeads
			if (warzoneConfig.containsKey("blockHeads")) {
				warzone.setBlockHeads(warzoneConfig.getBoolean("blockHeads"));
			}

			// spawnStyle
			String spawnStyle = warzoneConfig.getString("spawnStyle");
			if (spawnStyle != null && !spawnStyle.equals("")) {
				warzone.setSpawnStyle(TeamSpawnStyle.getStyleFromString(spawnStyle));
			}

			// flagReturn
			String flagReturn = warzoneConfig.getString("flagReturn");
			if (flagReturn != null && !flagReturn.equals("")) {
				warzone.setFlagReturn(FlagReturn.getFromString(flagReturn));
			}

			// reward
			String rewardStr = warzoneConfig.getString("reward");
			if (rewardStr != null && !rewardStr.equals("")) {
				fromStringToLoadout(rewardStr, warzone.getReward());
			}

			// unbreakableZoneBlocks
			if (warzoneConfig.containsKey("unbreakableZoneBlocks")) {
				warzone.setUnbreakableZoneBlocks(warzoneConfig.getBoolean("unbreakableZoneBlocks"));
			}

			// disabled
			if (warzoneConfig.containsKey("disabled")) {
				warzone.setDisabled(warzoneConfig.getBoolean("disabled"));
			}

			// noCreatures
			if (warzoneConfig.containsKey("noCreatures")) {
				warzone.setNoCreatures(warzoneConfig.getBoolean("noCreatures"));
			}
			
			// glassWalls
			if (warzoneConfig.containsKey("glassWalls")) {
				warzone.setGlassWalls(warzoneConfig.getBoolean("glassWalls"));
			}
			
			// pvpInZone
			if (warzoneConfig.containsKey("pvpInZone")) {
				warzone.setPvpInZone(warzoneConfig.getBoolean("pvpInZone"));
			}
			
			// instaBreak
			if (warzoneConfig.containsKey("instaBreak")) {
				warzone.setInstaBreak(warzoneConfig.getBoolean("instaBreak"));
			}
			
			// noDrops
			if (warzoneConfig.containsKey("noDrops")) {
				warzone.setNoDrops(warzoneConfig.getBoolean("noDrops"));
			}
			
			// noHunger
			if (warzoneConfig.containsKey("noHunger")) {
				warzone.setNoHunger(warzoneConfig.getBoolean("noHunger"));
			}
			
			// saturation
			if (warzoneConfig.containsKey("saturation")) {
				warzone.setSaturation(warzoneConfig.getInt("saturation"));
			}
			
			// minPlayers
			if (warzoneConfig.containsKey("minPlayers")) {
				warzone.setMinPlayers(warzoneConfig.getInt("minPlayers"));
			}
			
			// minTeams
			if (warzoneConfig.containsKey("minTeams")) {
				warzone.setMinTeams(warzoneConfig.getInt("minTeams"));
			}

			// resetOnEmpty
			if (warzoneConfig.containsKey("resetOnEmpty")) {
				warzone.setResetOnEmpty(warzoneConfig.getBoolean("resetOnEmpty"));
			}

			// resetOnLoad
			if (warzoneConfig.containsKey("resetOnLoad")) {
				warzone.setResetOnLoad(warzoneConfig.getBoolean("resetOnLoad"));
			}

			// resetOnUnload
			if (warzoneConfig.containsKey("resetOnUnload")) {
				warzone.setResetOnUnload(warzoneConfig.getBoolean("resetOnUnload"));
			}

			// rallyPoint
			String rallyPointStr = warzoneConfig.getString("rallyPoint");
			if (rallyPointStr != null && !rallyPointStr.equals("")) {
				String[] rallyPointStrSplit = rallyPointStr.split(",");

				int rpX = Integer.parseInt(rallyPointStrSplit[0]);
				int rpY = Integer.parseInt(rallyPointStrSplit[1]);
				int rpZ = Integer.parseInt(rallyPointStrSplit[2]);
				Location rallyPoint = new Location(world, rpX, rpY, rpZ);
				warzone.setRallyPoint(rallyPoint);
			}

			// monuments
			String monumentsStr = warzoneConfig.getString("monuments");
			if (monumentsStr != null && !monumentsStr.equals("")) {
				String[] monumentsSplit = monumentsStr.split(";");
				warzone.getMonuments().clear();
				for (String monumentStr : monumentsSplit) {
					if (monumentStr != null && !monumentStr.equals("")) {
						String[] monumentStrSplit = monumentStr.split(",");
						int monumentX = Integer.parseInt(monumentStrSplit[1]);
						int monumentY = Integer.parseInt(monumentStrSplit[2]);
						int monumentZ = Integer.parseInt(monumentStrSplit[3]);
						Monument monument = new Monument(monumentStrSplit[0], warzone, new Location(world, monumentX, monumentY, monumentZ));
						warzone.getMonuments().add(monument);
					}
				}
			}

			// lobby
			String lobbyStr = warzoneConfig.getString("lobby");

			warzoneConfig.close();

			if (createNewVolume) {
				ZoneVolume zoneVolume = new ZoneVolume(warzone.getName(), world, warzone); // VolumeMapper.loadZoneVolume(warzone.getName(), warzone.getName(), war, warzone.getWorld(), warzone);
				warzone.setVolume(zoneVolume);
			}

			// monument blocks
			for (Monument monument : warzone.getMonuments()) {
				monument.setVolume(VolumeMapper.loadVolume(monument.getName(), warzone.getName(), world));
			}

			// team spawn blocks
			for (Team team : warzone.getTeams()) {
				team.setSpawnVolume(VolumeMapper.loadVolume(team.getName(), warzone.getName(), world));
				if (team.getTeamFlag() != null) {
					team.setFlagVolume(VolumeMapper.loadVolume(team.getName() + "flag", warzone.getName(), world));
				}
			}

			// lobby
			BlockFace lobbyFace = null;
			if (lobbyStr != null && !lobbyStr.equals("")) {
				String[] lobbyStrSplit = lobbyStr.split(",");
				if (lobbyStrSplit.length > 0) {
					// lobby orientation
					if (lobbyStrSplit[0].equals("south")) {
						lobbyFace = BlockFace.SOUTH;
					} else if (lobbyStrSplit[0].equals("east")) {
						lobbyFace = BlockFace.EAST;
					} else if (lobbyStrSplit[0].equals("north")) {
						lobbyFace = BlockFace.NORTH;
					} else if (lobbyStrSplit[0].equals("west")) {
						lobbyFace = BlockFace.WEST;
					}
					
					// lobby world
					World lobbyWorld = world;	// by default, warzone world
					if (lobbyStrSplit.length > 1) {
						World strWorld = War.war.getServer().getWorld(lobbyStrSplit[1]);
						if (strWorld != null) {
							lobbyWorld = strWorld;
						}
					}
					
					// create the lobby
					Volume lobbyVolume = VolumeMapper.loadVolume("lobby", warzone.getName(), lobbyWorld);
					ZoneLobby lobby = new ZoneLobby(warzone, lobbyFace, lobbyVolume);
					warzone.setLobby(lobby);
				}
			}

			return warzone;
		}
		return null;
	}

	public static void save(Warzone warzone, boolean saveAllBlocks) {
		(new File(War.war.getDataFolder().getPath() + "/dat/warzone-" + warzone.getName())).mkdir();
		PropertiesFile warzoneConfig = new PropertiesFile(War.war.getDataFolder().getPath() + "/warzone-" + warzone.getName() + ".txt");
		// war.getLogger().info("Saving warzone " + warzone.getName() + "...");

		// name
		warzoneConfig.setString("name", warzone.getName());

		// world
		warzoneConfig.setString("world", warzone.getWorld().getName()); // default for now

		// teleport
		String teleportStr = "";
		Location tele = warzone.getTeleport();
		if (tele != null) {
			int intYaw = 0;
			if (tele.getYaw() >= 0) {
				intYaw = (int) (tele.getYaw() % 360);
			} else {
				intYaw = (int) (360 + (tele.getYaw() % 360));
			}
			teleportStr = tele.getBlockX() + "," + tele.getBlockY() + "," + tele.getBlockZ() + "," + intYaw;
		}
		warzoneConfig.setString("teleport", teleportStr);

		// teams
		String teamsStr = "";
		List<Team> teams = warzone.getTeams();
		for (Team team : teams) {
			Location spawn = team.getTeamSpawn();
			int intYaw = 0;
			if (spawn.getYaw() >= 0) {
				intYaw = (int) (spawn.getYaw() % 360);
			} else {
				intYaw = (int) (360 + (spawn.getYaw() % 360));
			}
			teamsStr += team.getName() + "," + spawn.getBlockX() + "," + spawn.getBlockY() + "," + spawn.getBlockZ() + "," + intYaw + ";";
		}
		warzoneConfig.setString("teams", teamsStr);

		// team flags
		String teamFlagsStr = "";;
		for (Team team : teams) {
			if (team.getFlagVolume() != null) {
				Location flag = team.getTeamFlag();
				int intYaw = 0;
				if (flag.getYaw() >= 0) {
					intYaw = (int) (flag.getYaw() % 360);
				} else {
					intYaw = (int) (360 + (flag.getYaw() % 360));
				}
				teamFlagsStr += team.getName() + "," + flag.getBlockX() + "," + flag.getBlockY() + "," + flag.getBlockZ() + "," + intYaw + ";";
			}
		}
		warzoneConfig.setString("teamFlags", teamFlagsStr);

		// ff
		warzoneConfig.setBoolean("friendlyFire", warzone.getFriendlyFire());

		// loadout
		String loadoutStr = "";
		HashMap<Integer, ItemStack> items = warzone.getLoadout();
		warzoneConfig.setString("loadout", fromLoadoutToString(items));
		
		// defaultExtraLoadouts
		String extraLoadoutsStr = "";
		for (String name : warzone.getExtraLoadouts().keySet()) {
			extraLoadoutsStr += name + ",";
			
			HashMap<Integer, ItemStack> loadout = warzone.getExtraLoadouts().get(name);
			warzoneConfig.setString(name + "Loadout", fromLoadoutToString(loadout));
		}
		warzoneConfig.setString("extraLoadouts", extraLoadoutsStr);

		// authors
		warzoneConfig.setString("author", warzone.getAuthorsString());
		
		// life pool
		warzoneConfig.setInt("lifePool", warzone.getLifePool());

		// monument heal
		warzoneConfig.setInt("monumentHeal", warzone.getMonumentHeal());

		// autoAssignOnly
		warzoneConfig.setBoolean("autoAssignOnly", warzone.isAutoAssignOnly());

		// flagPointsOnly-+
		warzoneConfig.setBoolean("flagPointsOnly", warzone.isFlagPointsOnly());

		// team cap
		warzoneConfig.setInt("teamCap", warzone.getTeamCap());

		// score cap
		warzoneConfig.setInt("scoreCap", warzone.getScoreCap());

		// blockHeads
		warzoneConfig.setBoolean("blockHeads", warzone.isBlockHeads());

		// spawnStyle
		warzoneConfig.setString("spawnStyle", warzone.getSpawnStyle().toString());

		// flagReturn
		warzoneConfig.setString("flagReturn", warzone.getFlagReturn().toString());

		// reward
		String rewardStr = "";
		HashMap<Integer, ItemStack> rewardItems = warzone.getReward();
		warzoneConfig.setString("reward", fromLoadoutToString(rewardItems));

		// unbreakableZoneBlocks
		warzoneConfig.setBoolean("unbreakableZoneBlocks", warzone.isUnbreakableZoneBlocks());

		// disabled
		warzoneConfig.setBoolean("disabled", warzone.isDisabled());

		// noCreatures
		warzoneConfig.setBoolean("noCreatures", warzone.isNoCreatures());
		
		// glassWalls
		warzoneConfig.setBoolean("glassWalls", warzone.isGlassWalls());
		
		// pvpInZone
		warzoneConfig.setBoolean("pvpInZone", warzone.isPvpInZone());
		
		// instaBreak
		warzoneConfig.setBoolean("instaBreak", warzone.isInstaBreak());
		
		// noDrops
		warzoneConfig.setBoolean("noDrops", warzone.isNoDrops());
		
		// noHunger
		warzoneConfig.setBoolean("noHunger", warzone.isNoHunger());
		
		// saturation
		warzoneConfig.setInt("saturation", warzone.getSaturation());
		
		// minPlayers
		warzoneConfig.setInt("minPlayers", warzone.getMinPlayers());
		
		// minTeams
		warzoneConfig.setInt("minTeams", warzone.getMinTeams());

		// resetOnEmpty
		warzoneConfig.setBoolean("resetOnEmpty", warzone.isResetOnEmpty());

		// resetOnLoad
		warzoneConfig.setBoolean("resetOnLoad", warzone.isResetOnLoad());

		// resetOnUnload
		warzoneConfig.setBoolean("resetOnUnload", warzone.isResetOnUnload());

		// rallyPoint
		String rpStr = "";
		Location rp = warzone.getRallyPoint();
		if (rp != null) {
			rpStr = rp.getBlockX() + "," + rp.getBlockY() + "," + rp.getBlockZ();
		}
		warzoneConfig.setString("rallyPoint", rpStr);

		// defaultDropLootOnDeath
		// warzoneConfig.setBoolean("dropLootOnDeath", warzone.isDropLootOnDeath());

		// monuments
		String monumentsStr = "";
		List<Monument> monuments = warzone.getMonuments();
		for (Monument monument : monuments) {
			Location monumentLoc = monument.getLocation();
			monumentsStr += monument.getName() + "," + monumentLoc.getBlockX() + "," + monumentLoc.getBlockY() + "," + monumentLoc.getBlockZ() + ";";
		}
		warzoneConfig.setString("monuments", monumentsStr);

		// lobby
		String lobbyStr = "";
		if (warzone.getLobby() != null) {
			if (BlockFace.SOUTH == warzone.getLobby().getWall()) {
				lobbyStr = "south";
			} else if (BlockFace.EAST == warzone.getLobby().getWall()) {
				lobbyStr = "east";
			} else if (BlockFace.NORTH == warzone.getLobby().getWall()) {
				lobbyStr = "north";
			} else if (BlockFace.WEST == warzone.getLobby().getWall()) {
				lobbyStr = "west";
			}
		}
		warzoneConfig.setString("lobby", lobbyStr + "," + warzone.getLobby().getVolume().getWorld().getName());

		warzoneConfig.save();
		warzoneConfig.close();

		// monument blocks
		for (Monument monument : monuments) {
			VolumeMapper.save(monument.getVolume(), warzone.getName());
		}

		// team spawn & flag blocks
		for (Team team : teams) {
			VolumeMapper.save(team.getSpawnVolume(), warzone.getName());
			if (team.getFlagVolume() != null) {
				VolumeMapper.save(team.getFlagVolume(), warzone.getName());
			}
		}

		if (warzone.getLobby() != null) {
			VolumeMapper.save(warzone.getLobby().getVolume(), warzone.getName());
		}
	}

	public static void delete(String name) {
		File zoneFolder = new File(War.war.getDataFolder().getPath() + "/dat/warzone-" + name);
		File[] files = zoneFolder.listFiles();
		for (File file : files) {
			boolean deletedData = file.delete();
			if (!deletedData) {
				War.war.log("Failed to delete file " + file.getName(), Level.WARNING);
			}
		}
		boolean deletedData = zoneFolder.delete();
		if (!deletedData) {
			War.war.log("Failed to delete folder " + zoneFolder.getName(), Level.WARNING);
		}
		File zoneFile = new File(War.war.getDataFolder().getPath() + "/warzone-" + name + ".txt");
		deletedData = zoneFile.delete();
		if (!deletedData) {
			War.war.log("Failed to delete file " + zoneFile.getName(), Level.WARNING);
		}
	}
	
	private static String fromLoadoutToString(HashMap<Integer, ItemStack> loadout) {
		String loadoutString = "";
		for (Integer slot : loadout.keySet()) {
			ItemStack item = loadout.get(slot);
			if (item != null) {
				loadoutString += item.getTypeId() + "," + item.getAmount() + "," + slot + "," + item.getDurability() + "," + item.getData().getData();
				if (item.getEnchantments().keySet().size() > 0) {
					String enchantmentsStr = "";
					for (Enchantment enchantment : item.getEnchantments().keySet()) {
						enchantmentsStr += enchantment.getId() + ":" + item.getEnchantments().get(enchantment) + "::";
					}
					loadoutString += "," + enchantmentsStr;
				}
			}
			loadoutString += ";";
		}
		return loadoutString;
	}
	
	private static void fromStringToLoadout(String loadoutString, HashMap<Integer, ItemStack> destinationLoadout) {
		String[] rewardStrSplit = loadoutString.split(";");
		destinationLoadout.clear();
		for (String itemStr : rewardStrSplit) {
			if (itemStr != null && !itemStr.equals("")) {
				String[] itemStrSplit = itemStr.split(",");
				ItemStack item = null;
				if (itemStrSplit.length == 3) {
					item = new ItemStack(Integer.parseInt(itemStrSplit[0]), Integer.parseInt(itemStrSplit[1]));
				} else if (itemStrSplit.length == 5) {
					short durability = Short.parseShort(itemStrSplit[3]);
					item = new ItemStack(Integer.parseInt(itemStrSplit[0]), Integer.parseInt(itemStrSplit[1]), durability, Byte.parseByte(itemStrSplit[4]));
					item.setDurability(durability);
				} else if (itemStrSplit.length == 6) {
					short durability = Short.parseShort(itemStrSplit[3]);
					item = new ItemStack(Integer.parseInt(itemStrSplit[0]), Integer.parseInt(itemStrSplit[1]), durability, Byte.parseByte(itemStrSplit[4]));
					item.setDurability(durability);
					
					// enchantments
					String[] enchantmentsSplit = itemStrSplit[5].split("::");
					for (String enchantmentStr : enchantmentsSplit) {
						if (!enchantmentStr.equals("")) {
							String[] enchantmentSplit = enchantmentStr.split(":");
							int enchantId = Integer.parseInt(enchantmentSplit[0]);
							int level = Integer.parseInt(enchantmentSplit[1]);
							item.addEnchantment(Enchantment.getById(enchantId), level);
						}
					}
				}
				destinationLoadout.put(Integer.parseInt(itemStrSplit[2]), item);
			}
		}
	}	
}
