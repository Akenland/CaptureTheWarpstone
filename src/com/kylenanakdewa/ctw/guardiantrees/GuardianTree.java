package com.kylenanakdewa.ctw.guardiantrees;

import java.util.HashSet;
import java.util.Set;

import com.kylenanakdewa.core.common.ConfigAccessor;
import com.kylenanakdewa.core.realms.Realm;
import com.kylenanakdewa.ctw.CTWPlugin;
import com.kylenanakdewa.ctw.WarpstoneCaptureData;
import com.kylenanakdewa.warpstones.Warpstone;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * A Guardian Tree that provides powers when multiple stones are owned.
 * @author Kyle Nanakdewa
 */
public class GuardianTree {

    private static Set<GuardianTree> trees;

    /**
     * Sets up the Guardian Trees system.
     */
    public static void enableGuardianTrees(CTWPlugin plugin){

        // Load trees from file
        ConfigurationSection file = new ConfigAccessor("trees.yml", plugin).getConfig();
        for(String treeName : file.getKeys(false)){
            PotionEffect effect = new PotionEffect(PotionEffectType.getByName(file.getString(treeName+".effect.type")), 100, file.getInt(treeName+".effect.level"));
            Set<Warpstone> warpstones = new HashSet<Warpstone>();
            file.getStringList(treeName+".warpstones").forEach(wsName -> warpstones.add(Warpstone.get(wsName)));
            trees.add(new GuardianTree(file.getString(treeName), effect, warpstones));
        }

        // Schedule task
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, ()->grantAllPowers(), 20, 60);

    }

    /**
     * Grants powers for Guardian Trees as needed.
     */
    private static void grantAllPowers(){
        // Performance boost: skip if the world is empty
        if(CTWPlugin.getCTWWorld()!=null && CTWPlugin.getCTWWorld().getPlayers().isEmpty()) return;

        trees.forEach(tree -> tree.grantPowers());
    }


    private final String name;
    private final PotionEffect effect;
    private final Set<Warpstone> warpstones;

    private GuardianTree(String name, PotionEffect effect, Set<Warpstone> warpstones){
        this.name = name;
        this.effect = effect;
        this.warpstones = warpstones;
    }

    /**
     * Checks if a realm meets the requirements to gain power from a guardian tree.
     * @return the realm who owns the tree, or null if no realm does
     */
    public Realm getControllingRealm(){
        Realm realm = null;
        for(Warpstone warpstone : warpstones){
            WarpstoneCaptureData data = CTWPlugin.getWarpstoneCaptureData(warpstone);
            if(realm!=null && !data.getRealm().equals(realm)) return null;
            else realm = data.getRealm();
        }
        return realm;
    }

    /**
     * Grants powers to realm members if the tree is controlled.
     */
    private void grantPowers(){
        Realm realm = getControllingRealm();
        if(realm!=null){
            for(Player player : realm.getOnlinePlayers()){
                if(CTWPlugin.getCTWWorld()==null || player.getLocation().getWorld().equals(CTWPlugin.getCTWWorld())){
                    player.addPotionEffect(effect, true);
                }
            }
        }
    }
}