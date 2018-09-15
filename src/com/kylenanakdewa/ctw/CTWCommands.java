package com.kylenanakdewa.ctw;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.kylenanakdewa.core.common.prompts.Prompt;
import com.kylenanakdewa.core.realms.Realm;
import com.kylenanakdewa.warpstones.Warpstone;
import com.kylenanakdewa.warpstones.WarpstonesPlugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

/**
 * Commands for CTW.
 * @author Kyle Nanakdewa
 */
class CTWCommands implements TabExecutor {

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if(args.length>=2){

            // Realm - show info about specific Realm
            if(args[0].equalsIgnoreCase("realm")){
                Realm realm = CTWPlugin.getRealmProvider().getRealm(args[1]);
                if(realm==null) return false;

                Prompt prompt = new Prompt();
                prompt.addQuestion("&8--- &9CTW Realm: "+realm.getName()+" ("+realm.getIdentifier()+") &8---");

                prompt.addAnswer("Realm info", "command_realm "+realm.getIdentifier());
                if(CTWPlugin.getLastRealmCap(realm)!=null) prompt.addAnswer("Last cap (respawn point): "+CTWPlugin.getLastRealmCap(realm).getIdentifier(), "command_ctw info "+CTWPlugin.getLastRealmCap(realm).getIdentifier());

                for(Warpstone warpstone : WarpstonesPlugin.getWarpstones().values()){
                    WarpstoneCaptureData data = CTWPlugin.getWarpstoneCaptureData(warpstone);
                    if(data.getRealm().equals(realm)){
                        prompt.addAnswer(warpstone.getDisplayName()+" ("+warpstone.getIdentifier()+")", "command_ctw info "+warpstone.getIdentifier());
                    }
                }

                prompt.display(sender);
                return true;
            }

            // Tree - show info about Guardian Tree
            if(args[0].equalsIgnoreCase("tree") && GuardianTree.getTrees()!=null){
                GuardianTree tree = GuardianTree.getTree(args[1]);

                if(tree==null) return false;

                tree.getInfo().display(sender);
                return true;
            }


            Warpstone ws = Warpstone.get(args[1]);
            if(ws==null) return false;
            WarpstoneCaptureData wsData = CTWPlugin.getWarpstoneCaptureData(ws);

            // Info - show info about specific Warpstone
            if(args[0].equalsIgnoreCase("info")){
                wsData.getInfo().display(sender);
                return true;
            }

            // Enable - enable capturing a specific Warpstone
            if(args[0].equalsIgnoreCase("enable")){
                wsData.setCapturable(true);
                sender.sendMessage(ws.getDisplayName()+" ("+ws.getIdentifier()+") capturable: "+wsData.isCapturable());
                return true;
            }
            // Disable - disable capturing a specific Warpstone
            if(args[0].equalsIgnoreCase("disable")){
                wsData.setCapturable(false);
                sender.sendMessage(ws.getDisplayName()+" ("+ws.getIdentifier()+") capturable: "+wsData.isCapturable());
                return true;
            }

            // Reset - reset realm
            if(args[0].equalsIgnoreCase("reset")){
                wsData.setRealm(null);
                if(wsData.getRealm()!=null) sender.sendMessage(ws.getDisplayName()+" ("+ws.getIdentifier()+") realm: "+wsData.getRealm());
                else sender.sendMessage(ws.getDisplayName()+" ("+ws.getIdentifier()+") realm cleared");
                return true;
            }

            //SetRealm - set realm
            if(args[0].equalsIgnoreCase("setrealm")){
                Realm realm = CTWPlugin.getRealmProvider().getRealm(args[2]);
                if(realm==null) return false;
                wsData.setRealm(realm);
                if(wsData.getRealm()!=null) sender.sendMessage(ws.getDisplayName()+" ("+ws.getIdentifier()+") realm: "+wsData.getRealm());
                else sender.sendMessage(ws.getDisplayName()+" ("+ws.getIdentifier()+") realm cleared");
                return true;
            }

            // StopCap - stop cap in-progress
            if(args[0].equalsIgnoreCase("stopcap")){
                wsData.stopCapping();
                sender.sendMessage(ws.getDisplayName()+" ("+ws.getIdentifier()+") cap status cleared");
                return true;
            }
        }

        return false;
	}

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if(args.length<2){
            if(GuardianTree.getTrees()!=null) return Arrays.asList("info", "realm", "tree");
            return Arrays.asList("info", "realm");
        }
        if(args.length==2){
            if(args[0].equalsIgnoreCase("info")) return Arrays.asList("");
            if(args[0].equalsIgnoreCase("realm")){
                List<String> realmNames = new ArrayList<String>();
                CTWPlugin.getRealmProvider().getAllRealms().forEach(realm -> realmNames.add(realm.getIdentifier()));
                return realmNames;
            }
            if(args[0].equalsIgnoreCase("tree") && GuardianTree.getTrees()!=null){
                List<String> treeNames = new ArrayList<String>();
                GuardianTree.getTrees().forEach(tree -> treeNames.add(tree.getName()));
                return treeNames;
            }
        }
        return Arrays.asList("");
    }

}