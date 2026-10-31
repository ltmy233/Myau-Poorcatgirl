package poorcatgirl.command.commands;

import poorcatgirl.Poorcatgirl;
import poorcatgirl.command.Command;
import poorcatgirl.module.Module;
import poorcatgirl.util.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;

public class ListCommand extends Command {
    public ListCommand() {
        super(new ArrayList<>(Arrays.asList("list", "l", "modules", "poorcatgirl")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (!Poorcatgirl.moduleManager.modules.isEmpty()) {
            ChatUtil.sendFormatted(String.format("%sModules:&r", Poorcatgirl.clientName));
            for (Module module : Poorcatgirl.moduleManager.modules.values()) {
                ChatUtil.sendFormatted(String.format("%s»&r %s&r", module.isHidden() ? "&8" : "&7", module.formatModule()));
            }
        }
    }
}
