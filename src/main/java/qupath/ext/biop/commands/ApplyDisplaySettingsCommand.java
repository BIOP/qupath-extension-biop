package qupath.ext.biop.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.viewer.QuPathViewerPlus;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
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
        ImageData<BufferedImage> currentImageData = qupath.getImageData();

        ImageServer<BufferedImage> currentServer = currentImageData.getServer();

        List<ImageChannel> channels = currentImageData.getServer().getMetadata().getChannels();

        List<String> channel_names = channels.stream().map(ImageChannel::getName).collect(Collectors.toList());

        // Try to get an existing display if the image is currently open
        QuPathViewerPlus viewer = qupath.getViewers().stream()
                .filter(v -> v.getImageData() == currentImageData)
                .findFirst()
                .orElse(null);
        ImageDisplay display = viewer == null ? new ImageDisplay(currentImageData) : viewer.getImageDisplay();
        var available = display.availableChannels();

        List<Float> channel_min = available.stream().map(ChannelDisplayInfo::getMinDisplay).collect(Collectors.toList());
        List<Float> channel_max = available.stream().map(ChannelDisplayInfo::getMaxDisplay).collect(Collectors.toList());
        List<Integer> channel_colors = available.stream().map(ChannelDisplayInfo::getColor).collect(Collectors.toList());

        // Get all images from Project

        List<ProjectImageEntry<BufferedImage>> imageList = qupath.getProject().getImageList();

        AtomicInteger nImages = new AtomicInteger();
        AtomicInteger nIgnored = new AtomicInteger();
        imageList.forEach(entry -> {
            try {
                ImageData<BufferedImage> imageData = entry.readImageData();

                ImageServer<BufferedImage> server = entry.getServerBuilder().build();

                logger.debug("Ref Type: {} vs Current Type {}", currentImageData.getImageType(), imageData.getImageType());
                logger.debug("Ref nC: {} vs Current nC {}", currentServer.getMetadata().getSizeC(), server.getMetadata().getSizeC());

                if (currentImageData.getImageType().equals(imageData.getImageType()) && currentServer.getMetadata().getSizeC() == server.getMetadata().getSizeC()) {
                    nImages.getAndIncrement();
                    QPEx.setChannelColors(imageData, channel_colors.toArray(new Integer[0]));
                    QPEx.setChannelNames(imageData, channel_names.toArray(new String[0]));

                    for (int i = 0; i < channel_min.size(); i++) {
                        QPEx.setChannelDisplayRange(imageData, channel_names.get(i), channel_min.get(i), channel_max.get(i));
                    }
                    entry.saveImageData(imageData);
                } else {
                    int i = nIgnored.getAndIncrement();
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        });

        Dialogs.showConfirmDialog("Applied settings to "+nImages.get(), ""+nIgnored.get()+" images were ignored due to either wrong image type or channel number");
    }
}