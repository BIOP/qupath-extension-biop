package qupath.ext.biop;

import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.biop.commands.ApplyDisplaySettingsCommand;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.MenuTools;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class BIOPExtension implements QuPathExtension, GitHubProject {

    private static final Logger logger = LoggerFactory.getLogger(BIOPExtension.class);

    @Override
    public void installExtension(QuPathGUI qupath) {
        // initialize command
        qupath.installCommand("Extensions>BIOP>Apply Display Settings", new ApplyDisplaySettingsCommand(qupath));

        // initialize scripts
        BIOPScripts.install(qupath);
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
    public GitHubRepo getRepository() {
        return GitHubRepo.create("BIOP QuPath Extension", "biop", "qupath-extension-biop");
    }


    /**
     * Install all scripts that the BIOP places in the Resources/scripts folder
     * @author Olivier Burri
     */
    public static class BIOPScripts {

        private static void install(QuPathGUI qupath) {

            try {
                List<Path> scripts = getAllGroovyScripts();

                scripts.forEach(script -> {
                    String fileName = script.getFileName().toString();
                    // Make underscores into spaces
                    String name = fileName.substring(0, fileName.lastIndexOf('.')).replaceAll("_", " ");
                    // Use the file's path as a menu path. Need to remove 'scripts' from the path
                    String menu = "Extensions>BIOP>scripts>" + script.getParent().toString().replace("/scripts/", "").replaceAll("[/\\\\]+", ">");
                    String scriptContent = "null";
                    try {
                        scriptContent = Files.readString(script, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        logger.error( e.getLocalizedMessage() );
                    }
                    if (!scriptContent.equals("null")) {
                        String finalScriptContent = scriptContent;
                        MenuTools.addMenuItems(qupath.getMenu(menu, true),
                                new Action( name, e -> openScript(qupath, finalScriptContent)));
                    }
                });
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        private static List<Path> getAllGroovyScripts() throws IOException, URISyntaxException {
            URI uri = BIOPExtension.class.getResource("/scripts").toURI();
            Path myPath;
            if (uri.getScheme().equals("jar")) {
                FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap());
                myPath = fileSystem.getPath("/scripts");
            } else {
                myPath = Paths.get(uri);
            }
            Stream<Path> walk = Files.walk(myPath, 10);
            return walk.filter(p -> p.toString().endsWith("groovy")).sorted().collect(Collectors.toList());
        }
    }

    private static void openScript (QuPathGUI qupath, String script){
        var editor = qupath.getScriptEditor();
        if (editor == null) {
            logger.error("No script editor is available!");
            return;
        }
        qupath.getScriptEditor().showScript("", script);
    }
}
