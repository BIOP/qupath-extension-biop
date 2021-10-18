package qupath.ext.biop.commands;

import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.DirectServerChannelInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.plugins.CommandLinePluginRunner;
import qupath.lib.plugins.SimpleProgressMonitor;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ApplyDisplaySettingsCommand implements Runnable {


    final static Logger logger = LoggerFactory.getLogger(ImageDisplay.class);

    private QuPathGUI qupath;

    //ApplyDisplaySettingsCommand
    public ApplyDisplaySettingsCommand(final QuPathGUI qupath) {
        if (!Dialogs.showConfirmDialog("Apply Brightness And Contrast", "Apply current display settings to all images?\n\nWill apply on images with the same image type and number of channels."))
            return;
        this.qupath = qupath;

    }

    public void run() {

        SimpleProgressMonitor monitor = new CommandLinePluginRunner.CommandLineProgressMonitor();

        ImageData<BufferedImage> currentImageData = qupath.getImageData();
        currentImageData.removeProperty("qupath.lib.display.ImageDisplay");

        ImageServer<BufferedImage> currentServer = currentImageData.getServer();

        ObservableList<ChannelDisplayInfo> currentChannels = qupath.getViewer().getImageDisplay().availableChannels();
        // Careful with channel names that have (C1), (C2), etc...
        List<String> channel_names = currentChannels.stream().map(c -> {
                    if (c instanceof DirectServerChannelInfo)
                        return currentServer.getChannel(((DirectServerChannelInfo) c).getChannel()).getName();
                    else
                        return (c.getName());
                }
        ).collect(Collectors.toList());
        List<Float> channel_min = currentChannels.stream().map(ChannelDisplayInfo::getMinDisplay).collect(Collectors.toList());
        List<Float> channel_max = currentChannels.stream().map(ChannelDisplayInfo::getMaxDisplay).collect(Collectors.toList());
        List<Integer> channel_colors = currentChannels.stream().map(ChannelDisplayInfo::getColor).collect(Collectors.toList());

        // Get all images from Project
        List<ProjectImageEntry<BufferedImage>> imageList = qupath.getProject().getImageList();

        monitor.startMonitoring("Updating Display Settings on Project", imageList.size(), false);
        AtomicInteger index = new AtomicInteger();
        imageList.forEach(entry -> {
            try {
                ImageData<BufferedImage> imageData = entry.readImageData();
                monitor.updateProgress(index.getAndIncrement(), imageData.getServer().getMetadata().getName(), null);

                ImageServer<BufferedImage> server = entry.getServerBuilder().build();

                logger.debug("Ref Type: {} vs Current Type {}", currentImageData.getImageType(), imageData.getImageType());
                logger.debug("Ref nC: {} vs Current nC {}", currentServer.getMetadata().getSizeC(), server.getMetadata().getSizeC());

                if (currentImageData.getImageType().equals(imageData.getImageType()) && currentServer.getMetadata().getSizeC() == server.getMetadata().getSizeC()) {

                    QPEx.setChannelColors(imageData, channel_colors.toArray(new Integer[0]));
                    QPEx.setChannelNames(imageData, channel_names.toArray(new String[0]));

                    for (int i = 0; i < channel_min.size(); i++) {
                        QPEx.setChannelDisplayRange(imageData, channel_names.get(i), channel_min.get(i), channel_max.get(i));
                    }
                    entry.saveImageData(imageData);
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        });
        monitor.pluginCompleted("Done");
    }
}