package com.kylenanakdewa.ctw;

import java.util.HashSet;
import java.util.Set;

import com.kylenanakdewa.core.common.ConfigAccessor;
import com.kylenanakdewa.core.common.prompts.Prompt;
import com.kylenanakdewa.core.realms.Realm;
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

    /** The Guardian Trees on this server. */
    private static Set<GuardianTree> trees;

    /**
     * Sets up the Guardian Trees system. Should only be called once at plugin startup.
     */
    public static void enableGuardianTrees(CTWPlugin plugin){
        if(trees!=null) return;

        // Load trees from file
        trees = new HashSet<GuardianTree>();
        ConfigurationSection file = new ConfigAccessor("trees.yml", plugin).getConfig();
        for(String treeName : file.getKeys(false)){
            PotionEffectType effectType = PotionEffectType.getByName(file.getString(treeName+".effect.type").toUpperCase());
            if(effectType==null){
                Bukkit.getLogger().warning("[CTW Guardian Trees] Invalid effect for tree "+treeName+" - "+file.getString(treeName+".effect.type"));
                effectType = PotionEffectType.INCREASE_DAMAGE;
            }
            PotionEffect effect = new PotionEffect(effectType, 100, file.getInt(treeName+".effect.level"));
            Set<Warpstone> warpstones = new HashSet<Warpstone>();
            file.getStringList(treeName+".warpstones").forEach(wsName -> warpstones.add(Warpstone.get(wsName)));
            trees.add(new GuardianTree(treeName, effect, warpstones));
            Bukkit.getLogger().info("[CTW Guardian Trees] Added tree "+treeName+" - "+warpstones.size()+" stones - "+effectType.getName()+" effect");
        }

        Bukkit.getLogger().info("[CTW Guardian Trees] Found "+trees.size()+" trees");

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

    /**
     * Gets a set of Guardian Trees.
     * @return the set of trees on this server
     */
    public static Set<GuardianTree> getTrees(){
        return trees;
    }

    /**
     * Gets a specific Guardian Tree by name.
     * @param treeName the name of the tree to get
     * @return the tree, or null if it does not exist
     */
    public static GuardianTree getTree(String treeName){
        for(GuardianTree tree : trees) if(tree.getName().equalsIgnoreCase(treeName)) return tree;
        return null;
    }


    /** The unique name of the tree. */
    private final String name;
    /** The potion effect to grant to players who own this tree. */
    private final PotionEffect effect;
    /** The Warpstones that must be owned to own this tree. */
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
            if(realm!=null && data!=null && data.getRealm()!=null && !data.getRealm().equals(realm)) return null;
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
            for(Realm childRealm : realm.getChildRealms()){
                for(Player player : childRealm.getOnlinePlayers()){
                    if(CTWPlugin.getCTWWorld()==null || player.getLocation().getWorld().equals(CTWPlugin.getCTWWorld())){
                        player.addPotionEffect(effect, true);
                    }
                }
            }
        }
    }

    /**
     * Gets the name of this tree.
     * @return the name of the tree
     */
    public String getName(){
        return name;
    }
    /**
     * Gets the potion effect that is granted to players who own this tree.
     * @return the potion effect
     */
    public PotionEffect getEffect(){
        return effect;
    }
    /**
     * Gets the set of Warpstones that must be owned to own this tree.
     * @return the warpstones
     */
    public Set<Warpstone> getWarpstones(){
        return warpstones;
    }

    /**
     * Gets an info prompt about this tree.
     * @return a Prompt with information about this tree
     */
    public Prompt getInfo(){
        Prompt prompt = new Prompt();
        prompt.addQuestion("&8--- &9CTW Guardian Tree: "+name+" &8---");
        prompt.addQuestion("Effect: "+effect.getType().getName()+" "+(effect.getAmplifier()+1));
        Realm owningRealm = getControllingRealm();
        if(owningRealm!=null) prompt.addQuestion("Active for "+owningRealm.getColor()+owningRealm.getIdentifier());
        prompt.addQuestion("&8-- &9Warpstones &8--");
        for(Warpstone warpstone : warpstones){
            Realm realm = CTWPlugin.getWarpstoneCaptureData(warpstone).getRealm();
            String realmString = realm!=null ? " - Owned by "+realm.getColor()+realm.getIdentifier() : "";
            prompt.addAnswer(warpstone.getDisplayName()+" ("+warpstone.getIdentifier()+")"+realmString, "command_ctw info "+warpstone.getIdentifier());
        }
        return prompt;
    }
}