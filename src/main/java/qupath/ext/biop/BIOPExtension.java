package qupath.ext.biop;

import org.controlsfx.control.action.Action;
import qupath.ext.biop.commands.ApplyDisplaySettingsCommand;
import qupath.lib.common.Version;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.QuPathExtension;

public class BIOPExtension implements QuPathExtension, GitHubProject {

    @Override
    public void installExtension(QuPathGUI qupath) {
        qupath.installActions(ActionTools.getAnnotatedActions(new DisplayCommands(qupath)));
    }

    @Override
    public String getName() {
        return "BIOP QuPath Extension";
    }

    @Override
    public String getDescription() {
        return "A collection of tools useful at the BIOP to ease scripting in a multi-user facility. Created with love.";
    }

    @Override
    public Version getQuPathVersion() {
        return QuPathExtension.super.getQuPathVersion();
    }

    @Override
    public Version getVersion() { return Version.parse("1.0.3");
    }

    @Override
    public GitHubRepo getRepository() {
        return GitHubRepo.create("BIOP QuPath Extension", "biop", "qupath-extension-biop");
    }

    /**
     * Installs the "Apply Display Settings" command
     */
    public static class DisplayCommands {

        @ActionTools.ActionMenu("BIOP>Display>Apply Display Settings")
        @ActionTools.ActionDescription("Display commands")
        public final Action actionApplyDisplay;

        private DisplayCommands(QuPathGUI qupath) {
            actionApplyDisplay = qupath.createProjectAction(project -> new ApplyDisplaySettingsCommand(qupath).run());
        }
    }
}
