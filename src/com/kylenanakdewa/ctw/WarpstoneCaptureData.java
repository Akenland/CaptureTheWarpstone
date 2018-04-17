package com.kylenanakdewa.ctw;

import java.util.HashSet;
import java.util.Set;

import com.kylenanakdewa.core.characters.players.PlayerCharacter;
import com.kylenanakdewa.core.common.CommonColors;
import com.kylenanakdewa.core.common.Utils;
import com.kylenanakdewa.core.realms.Realm;
import com.kylenanakdewa.core.realms.RealmMember;
import com.kylenanakdewa.warpstones.Warpstone;
import com.kylenanakdewa.warpstones.WarpstoneSaveDataSection;
import com.kylenanakdewa.warpstones.events.WarpstoneActivateEvent;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

/**
 * Holds data about a capturable Warpstone.
 * @author Kyle Nanakdewa
 */
public class WarpstoneCaptureData extends WarpstoneSaveDataSection implements RealmMember {

	/** Whether this Warpstone can be captured. */
	private boolean isCapturable;
	/** The Realm that owns this Warpstone, or null if it is neutral. */
	private Realm realm;

	/** The Realm attempting to capture this Warpstone. Null if it's not under capture. */
	private Realm cappingRealm;
	/** The players capping this Warpstone. */
	private Set<Player> cappingPlayers;
	/** The remaining time to cap, in ticks. */
	private double capTime;
	/** The progress bar for capturing. */
	private BossBar progressBar;

	/** The task ID for the cap progression. */
	private int taskID;


	WarpstoneCaptureData(Warpstone warpstone, CTWPlugin plugin) {
		super(warpstone, plugin);

		// Determine if capturable based on global setting
		if(CTWPlugin.getCTWWorld()==null || warpstone.getLocation().getWorld().equals(CTWPlugin.getCTWWorld())){
			isCapturable = true;
			realm = CTWPlugin.getRealmProvider().getRealm(getString("realm"));
		}
	}


	/**
	 * Sets whether this Warpstone is capturable.
	 * <p>
	 * Setting to false will fully disable CTW for this Warpstone.
	 */
	public void setCapturable(boolean capturable){
		isCapturable = capturable;
	}
	/**
	 * Returns true if players can capture this Warpstone.
	 * @return true if this Warpstone is capturable.
	 */
	public boolean isCapturable(){
		return isCapturable;
	}


	@Override
	public Realm getRealm() {
		return realm;
	}
	@Override
	public void setRealm(Realm realm) {
		String warpstoneNameOrBlank = warpstone.getDisplayName()!=null ? warpstone.getDisplayName() : "";
		String warpstoneNameOrWarpstone = warpstone.getDisplayName()!=null ? warpstone.getDisplayName() : "Warpstone";

		// Notify losing realm
		if(this.realm!=null) this.realm.getOnlinePlayers().forEach(player -> player.sendTitle(ChatColor.RED+"Warpstone Lost!", warpstoneNameOrBlank));

		this.realm = realm;
		set("realm", realm.getIdentifier());
		stopCapping();

		// Notify all players that warpstone was captured
		Utils.notifyAll(CommonColors.INFO+"[CTW] "+ChatColor.WHITE+warpstoneNameOrWarpstone+CommonColors.MESSAGE+" was captured!");

		// Notify winning realm
		realm.getOnlinePlayers().forEach(player -> player.sendTitle(ChatColor.GREEN+"Warpstone Captured!", warpstoneNameOrBlank));
	}
	@Override
	public boolean isRealmOfficer() {
		return false;
	}


	/**
	 * Called when this Warpstone is activated, if part of the CTW game (is capturable).
	 * @param event the activation event
	 */
	void onActivation(WarpstoneActivateEvent event){
		PlayerCharacter character = PlayerCharacter.getCharacter(event.getPlayer());
		// If player is not in a realm, show error
		if(character.getRealm()==null){
			Utils.sendActionBar(event.getPlayer(), "This warpstone can only be used by realm members");
			event.setCancelled(true);
			return;
		}

		// If character's realm owns the Warpstone, activate as normal
		if(character.getRealm().equals(realm)) return;

		event.setCancelled(true);

		// If warpstone is being capped
		if(cappingRealm!=null){

			// If the warpstone is being capped by their own team, reduce required cap time
			if(character.getRealm().equals(cappingRealm) && !cappingPlayers.contains(event.getPlayer())){
				if(cappingPlayers.size()<4) capTime=capTime*(1.00-CTWPlugin.getCapTimeReduction());
				cappingPlayers.add(event.getPlayer());
				progressBar.addPlayer(event.getPlayer());
				return;
			}

			// If being capped by another team, show error
			else {
				Utils.sendActionBar(event.getPlayer(), "This warpstone is already being captured!");
				return;
			}
		}

		// Otherwise, start capping!
		startCapping(character.getRealm(), event.getPlayer());
	}


	/**
	 * Starts capping process.
	 * @param realm the realm who is capping
	 * @param player the player who is capping
	 */
	private void startCapping(Realm realm, Player player){
		// Start cap
		cappingRealm = realm;
		cappingPlayers = new HashSet<Player>();
		cappingPlayers.add(player);
		capTime = CTWPlugin.getBaseCapTime()*20;

		String warpstoneName = warpstone.getDisplayName()!=null ? warpstone.getDisplayName() : "Warpstone";

		// Notify original warpstone owner
		if(this.realm!=null) this.realm.getOnlinePlayers().forEach(losingPlayer -> player.sendMessage(CommonColors.INFO+"[CTW] "+ChatColor.WHITE+warpstoneName+CommonColors.MESSAGE+" is being captured!"));

		// Notify capping player, and set up progress bar
		player.sendTitle("", ChatColor.BLUE+"Capturing "+warpstoneName);

		progressBar = Bukkit.createBossBar("Capturing "+warpstoneName, BarColor.BLUE, BarStyle.SOLID);
		progressBar.addPlayer(player);
		progressBar.setVisible(true);

		// Start timer
		taskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(getPlugin(), () -> {
			// Update timer
			capTime-=20;
			// If cap time runs out, set new owner!
			if(capTime==0){
				setRealm(cappingRealm);
			}
			progressBar.setProgress(capTime/(CTWPlugin.getBaseCapTime()*20));
			progressBar.setTitle("Capturing "+warpstoneName+": "+getCapTimeString()+" remaining");

			// Remove players if they go too far or disconnect
			Set<Player> checkPlayers = new HashSet<Player>(cappingPlayers);
			checkPlayers.removeIf(cappingPlayer -> !cappingPlayer.isOnline() || cappingPlayer.getLocation().distanceSquared(warpstone.getLocation()) > Math.pow(CTWPlugin.getMaxCapDistance(),2));
			if(checkPlayers.size()==0){
				stopCapping();
				cappingPlayers.forEach(cappingPlayer -> cappingPlayer.sendTitle("", CommonColors.ERROR+"Failed to capture "+warpstoneName));
				if(this.realm!=null) this.realm.getOnlinePlayers().forEach(losingPlayer -> player.sendMessage(CommonColors.INFO+"[CTW] "+ChatColor.WHITE+warpstoneName+CommonColors.MESSAGE+" is no longer being captured."));
			}
		}, 0, 20);
	}

	/**
	 * Stops capping.
	 */
	private void stopCapping(){
		cappingRealm = null;
		cappingPlayers.clear();
		capTime = 0;
		progressBar.setVisible(false);
		progressBar.removeAll();
		Bukkit.getScheduler().cancelTask(taskID);
	}

	/**
	 * Gets remaining time as a M:SS string.
	 */
	private String getCapTimeString(){
		double seconds = capTime/20;
		int minutes = 0;
		while(seconds>=60){
			minutes++;
			seconds-=60;
		}
		return minutes+":"+Math.round(seconds);
	}

}