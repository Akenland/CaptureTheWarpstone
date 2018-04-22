package com.kylenanakdewa.ctw;

import com.kylenanakdewa.core.characters.players.PlayerCharacter;
import com.kylenanakdewa.core.common.CommonColors;
import com.kylenanakdewa.core.common.Utils;
import com.kylenanakdewa.warpstones.WarpPlayer;
import com.kylenanakdewa.warpstones.Warpstone;
import com.kylenanakdewa.warpstones.events.PlayerWarpEvent;
import com.kylenanakdewa.warpstones.events.WarpstoneActivateEvent;
import com.kylenanakdewa.warpstones.events.PlayerWarpEvent.WarpCause;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

/**
 * Listener for Capture The Warpstone.
 * <p>
 * Handles the following:
 * <ul>
 * <li>Warpstone Activation (for capturing, and blocking use of captured warpstones)
 * <li>Warping (blocking use of captured warpstones, and blocking warp commands on CTW world)
 * <li>Teleporting (blocking use of any teleport commands or portals)
 * </ul>
 * @author Kyle Nanakdewa
 */
public final class CTWListener implements Listener {

	/**
	 *  If Warpstone is activated, and it is capturable, CTW handles it.
	 */
	@EventHandler
	public void onWarpstoneActivation(WarpstoneActivateEvent event){
		WarpstoneCaptureData data = CTWPlugin.getWarpstoneCaptureData(event.getWarpstone());

		// Only pass it on if the warpstone is capturable
		if(data.isCapturable()){
			data.onActivation(event);
		}
	}


	/**
	 * If player warps, and the destination is a warpstone they don't own, or they are on the CTW world (if set), block warp.
	 */
	@EventHandler
	public void onWarp(PlayerWarpEvent event){
		// Override spawn warpstone
		if(event.isSpawnWarpstone() && CTWPlugin.getCTWSpawn()!=null && (CTWPlugin.getCTWWorld()==null || event.getPlayer().getLocation().getWorld().equals(CTWPlugin.getCTWWorld()))){
			event.setCancelled(true);
			new WarpPlayer(event.getPlayer()).warp(CTWPlugin.getCTWSpawn(), false, event.getCause());
			return;
		}

		// Prevent warping to lost warpstones
		WarpstoneCaptureData data = CTWPlugin.getWarpstoneCaptureData(event.getWarpstone());
		PlayerCharacter character = PlayerCharacter.getCharacter(event.getPlayer());
		if(data.isCapturable() && !event.getCause().equals(WarpCause.SHARD) && (character.getRealm()==null || data.getRealm()==null || !data.getRealm().equals(character.getRealm()))){
			Utils.sendActionBar(event.getPlayer(), CommonColors.ERROR+"Your realm has lost this Warpstone!");
			event.setCancelled(true);
			return;
		}

		// Prevent warp command on CTW world
		if(CTWPlugin.isTeleportationBlocked() && !event.getPlayer().hasPermission("warpstones.tp.nolimits") && event.getCause().equals(WarpCause.COMMAND) && (CTWPlugin.getCTWWorld()==null || event.getPlayer().getLocation().getWorld().equals(CTWPlugin.getCTWWorld()))){
			Utils.sendActionBar(event.getPlayer(), CommonColors.ERROR+"You must use Warpstones on this world!");
			event.setCancelled(true);
			return;
		}
	}


	/**
	 * Block portal use on the CTW world.
	 */
	@EventHandler
	public void onTeleport(PlayerTeleportEvent event){
		if(!CTWPlugin.isTeleportationBlocked()) return;

		TeleportCause cause = event.getCause();
		if((CTWPlugin.getCTWWorld()==null || event.getFrom().getWorld().equals(CTWPlugin.getCTWWorld()) || event.getTo().getWorld().equals(CTWPlugin.getCTWWorld())) && 
		// Block if cause is a portal
		(cause.equals(TeleportCause.END_PORTAL) || cause.equals(TeleportCause.NETHER_PORTAL))){
			Utils.sendActionBar(event.getPlayer(), CommonColors.ERROR+"You must use Warpstones on this world!");
			event.setCancelled(true);
		}
	}

	/**
	 * Block teleportation commands on the CTW world.
	 */
	@EventHandler
	public void onCommand(PlayerCommandPreprocessEvent event){
		if(!CTWPlugin.isTeleportationBlocked() || event.getPlayer().hasPermission("warpstones.tp.nolimits")) return;

		if(CTWPlugin.getCTWWorld()==null || event.getPlayer().getLocation().getWorld().equals(CTWPlugin.getCTWWorld())){
			String command = event.getMessage().toLowerCase().split(" ", 2)[0];
			if(command.endsWith("tp") || command.endsWith("tpa") || command.endsWith("tphere") || command.endsWith("tpahere")
			 || command.endsWith("warp") || command.endsWith("ws") || command.endsWith("warpstones")
			 || command.endsWith("spawn") || command.endsWith("home") || command.endsWith("last")){
				Utils.sendActionBar(event.getPlayer(), CommonColors.ERROR+"You must use Warpstones on this world!");
				event.setCancelled(true);
			}
		}
	}

	/**
	 * Block bed usage on CTW world.
	 */
	@EventHandler
	public void onBedEnter(PlayerBedEnterEvent event){
		if(CTWPlugin.getCTWWorld()==null || event.getPlayer().getLocation().getWorld().equals(CTWPlugin.getCTWWorld())){
			Utils.sendActionBar(event.getPlayer(), CommonColors.ERROR+"You must use Warpstones on this world!");
			event.setCancelled(true);
		}
	}


	/**
	 * Respawn players at last Warpstone they capped, or the CTW spawn.
	 */
	@EventHandler
	public void onRespawn(PlayerRespawnEvent event){
		PlayerCharacter character = PlayerCharacter.getCharacter(event.getPlayer());
		if(CTWPlugin.getCTWWorld()==null || event.getPlayer().getLocation().getWorld().equals(CTWPlugin.getCTWWorld())){
			Warpstone respawnLoc = CTWPlugin.getLastRealmCap(character.getRealm());
			String message = CommonColors.INFO+"Respawning at the last Warpstone your realm captured";

			// If that's null, use spawn instead
			if(respawnLoc==null){
				respawnLoc = CTWPlugin.getCTWSpawn();
				message = CommonColors.INFO+"Respawning at CTW spawn";
			}

			if(respawnLoc!=null){
				event.setRespawnLocation(respawnLoc.getLocation());
				Utils.sendActionBar(event.getPlayer(), message);
			}
		}
	}

}