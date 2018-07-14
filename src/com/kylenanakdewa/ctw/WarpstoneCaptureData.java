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
import com.kylenanakdewa.warpstones.items.ItemListener;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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
	/** The progress bar for losing. */
	private BossBar losingBar;

	/** The task ID for the cap progression. */
	private int taskID;


	WarpstoneCaptureData(Warpstone warpstone, CTWPlugin plugin) {
		super(warpstone, plugin);

		// Determine if capturable based on global setting
		if(!warpstone.equals(CTWPlugin.getCTWSpawn()) && !warpstone.equals(Warpstone.getSpawn()) && (CTWPlugin.getCTWWorld()==null || warpstone.getLocation().getWorld().equals(CTWPlugin.getCTWWorld()))){
			isCapturable = true;
			realm = CTWPlugin.getRealmProvider().getRealm(data.getString("realm"));
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
		if(this.realm!=null){
			this.realm.getOnlinePlayers().forEach(player -> player.sendTitle(ChatColor.RED+"Warpstone Lost!", warpstoneNameOrBlank));
			this.realm.getChildRealms().forEach(childRealm -> childRealm.getOnlinePlayers().forEach(childPlayer -> childPlayer.sendTitle(ChatColor.RED+"Warpstone Lost!", warpstoneNameOrBlank)));
		}

		// Give warp dust to capping players
		cappingPlayers.forEach(player -> player.getInventory().addItem(ItemListener.getRandomWarpDust()));

		this.realm = realm;
		data.set("realm", realm.getIdentifier());
		CTWPlugin.setLastRealmCap(realm, warpstone);
		stopCapping();

		// Notify all players that warpstone was captured
		Utils.notifyAll(CommonColors.INFO+"[CTW] "+ChatColor.WHITE+warpstoneNameOrWarpstone+CommonColors.MESSAGE+" was captured!");

		// Sound effect
		warpstone.getLocation().getWorld().playSound(warpstone.getLocation(), Sound.ITEM_BOTTLE_FILL_DRAGONBREATH, SoundCategory.AMBIENT, 1, 0);
		Bukkit.getScheduler().scheduleSyncDelayedTask(getPlugin(), () -> warpstone.getLocation().getWorld().playSound(warpstone.getLocation(), Sound.BLOCK_NOTE_BELL, 1, 0.8f), 12);

		// Notify winning realm
		realm.getOnlinePlayers().forEach(player -> player.sendTitle(ChatColor.GREEN+"Warpstone Captured!", warpstoneNameOrBlank));
		realm.getChildRealms().forEach(childRealm -> childRealm.getOnlinePlayers().forEach(childPlayer -> childPlayer.sendTitle(ChatColor.GREEN+"Warpstone Captured!", warpstoneNameOrBlank)));

		save();
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
		Realm playerRealm = character.getRealm();
		if(playerRealm!=null && playerRealm.getTopParentRealm()!=null) playerRealm = playerRealm.getTopParentRealm();

		// If player is not in a realm, show error
		if(playerRealm==null){
			Utils.sendActionBar(event.getPlayer(), "This warpstone can only be used by realm members");
			event.setCancelled(true);
			return;
		}

		// If character's realm owns the Warpstone, activate as normal
		if(playerRealm.equals(realm)) return;

		event.setCancelled(true);

		// If warpstone is being capped
		if(cappingRealm!=null){

			// If the warpstone is being capped by their own team, reduce required cap time
			if(playerRealm.equals(cappingRealm) && !cappingPlayers.contains(event.getPlayer())){
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
		startCapping(playerRealm, event.getPlayer());
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
		Utils.notifyAll(CommonColors.INFO+"[CTW] "+ChatColor.WHITE+warpstoneName+CommonColors.MESSAGE+" is being captured!");

		// Notify capping player, and set up progress bar
		player.sendTitle("", ChatColor.BLUE+"Capturing "+warpstoneName);

		progressBar = Bukkit.createBossBar("Capturing "+warpstoneName, BarColor.BLUE, BarStyle.SOLID);
		losingBar = Bukkit.createBossBar("Losing "+warpstoneName, BarColor.RED, BarStyle.SOLID);
		
		// Start timer
		taskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(getPlugin(), () -> {
			// Update timer
			capTime-=20;
			// If cap time runs out, set new owner!
			if(capTime<1){
				setRealm(cappingRealm);
				return;
			}
			
			progressBar.setProgress(capTime/(CTWPlugin.getBaseCapTime()*20));
			progressBar.setTitle("Capturing "+warpstoneName+": "+getCapTimeString()+" remaining");
			realm.getOnlinePlayers().forEach(cappingPlayer -> progressBar.addPlayer(cappingPlayer));
			realm.getChildRealms().forEach(childRealm -> childRealm.getOnlinePlayers().forEach(childPlayer -> progressBar.addPlayer(childPlayer)));
			progressBar.setVisible(true);
			
			losingBar.setProgress(capTime/(CTWPlugin.getBaseCapTime()*20));
			losingBar.setTitle("Losing "+warpstoneName+": "+getCapTimeString()+" remaining");
			if(this.realm!=null){
				this.realm.getOnlinePlayers().forEach(losingPlayer -> losingBar.addPlayer(losingPlayer));
				this.realm.getChildRealms().forEach(childRealm -> childRealm.getOnlinePlayers().forEach(childPlayer -> losingBar.addPlayer(childPlayer)));
				losingBar.setVisible(true);
			} else {
				losingBar.removeAll();
				losingBar.setVisible(false);
			}

			// Sound effect
			warpstone.getLocation().getWorld().playSound(warpstone.getLocation(), Sound.BLOCK_NOTE_BASEDRUM, SoundCategory.AMBIENT, 1, 0.5f);
			Bukkit.getScheduler().scheduleSyncDelayedTask(getPlugin(), () -> warpstone.getLocation().getWorld().playSound(warpstone.getLocation(), Sound.BLOCK_NOTE_BASEDRUM, SoundCategory.AMBIENT, 1, 0.6f), 6);
			if(capTime<300){
				Bukkit.getScheduler().scheduleSyncDelayedTask(getPlugin(), () -> {
					warpstone.getLocation().getWorld().playSound(warpstone.getLocation(), Sound.BLOCK_NOTE_BASEDRUM, SoundCategory.AMBIENT, 1, 0.5f);
					Bukkit.getScheduler().scheduleSyncDelayedTask(getPlugin(), () -> warpstone.getLocation().getWorld().playSound(warpstone.getLocation(), Sound.BLOCK_NOTE_BASEDRUM, SoundCategory.AMBIENT, 1, 0.6f), 6);
				}, 10);
			}
			// Particle effect
			warpstone.getLocation().getWorld().spawnParticle(Particle.PORTAL, warpstone.getLocation().getX(), warpstone.getLocation().getY()+1, warpstone.getLocation().getZ(), 1200, 8, 8, 8, 2);

			// Remove players if they go too far or disconnect
			Set<Player> checkPlayers = new HashSet<Player>(cappingPlayers);
			Set<Player> markedRemoval = new HashSet<Player>();
			for(Player cappingPlayer : checkPlayers){
				if(!cappingPlayer.isOnline() || cappingPlayer.isDead() || (CTWPlugin.getCTWWorld()!=null && !cappingPlayer.getLocation().getWorld().equals(CTWPlugin.getCTWWorld())) || cappingPlayer.getLocation().distanceSquared(warpstone.getLocation()) > Math.pow(CTWPlugin.getMaxCapDistance(),2)){
					Utils.sendActionBar(cappingPlayer, CommonColors.ERROR+"You are too far away to capture "+warpstoneName);
					markedRemoval.add(cappingPlayer);

					// Increase cap time
					capTime += (CTWPlugin.getBaseCapTime()*20) * 0.20;
				}
			}
			checkPlayers.removeAll(markedRemoval);
			if(checkPlayers.size()==0){
				cappingPlayers.forEach(cappingPlayer -> cappingPlayer.sendTitle("", CommonColors.ERROR+"Failed to capture "+warpstoneName));
				Utils.notifyAll(CommonColors.INFO+"[CTW] "+ChatColor.WHITE+warpstoneName+CommonColors.MESSAGE+" is no longer being captured.");
				stopCapping();
			}
			cappingPlayers.removeAll(markedRemoval);
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
		losingBar.setVisible(false);
		losingBar.removeAll();
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
		String secString = Math.round(seconds)+"";
		if(secString.length()==1) secString = "0"+secString;
		return minutes+":"+secString;
	}

}