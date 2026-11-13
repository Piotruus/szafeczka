package pl.szafa;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SzafaCommand implements CommandExecutor {

    private final SzafaPlugin plugin;

    public SzafaCommand(SzafaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Tylko dla graczy.");
            return true;
        }
        if (args.length > 0 && (args[0].equalsIgnoreCase("podglad") || args[0].equalsIgnoreCase("podgląd"))) {
            if (plugin.activeOf(p, Cosmetic.Kind.COSTUME) == null) {
                plugin.msg(p, "&cNie masz aktywnego kostiumu.");
                return true;
            }
            plugin.costumeManager().startPreview(p);
            plugin.msg(p, "&aPodgląd kostiumu na sobie włączony na &f20 sekund&a. Zmień widok na F5. "
                    + "Kliknięcie w ekwipunek kończy podgląd.");
            return true;
        }
        plugin.menus().openMain(p);
        return true;
    }
}
