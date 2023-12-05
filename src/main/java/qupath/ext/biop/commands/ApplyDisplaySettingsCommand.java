package qupath.ext.biop.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ApplyDisplaySettingsCommand implements Runnable {


    final static Logger logger = LoggerFactory.getLogger(ImageDisplay.class);

    private QuPathGUI qupath;

    //ApplyDisplaySettingsCommand
    public ApplyDisplaySettingsCommand(final QuPathGUI qupath) {

        this.qupath = qupath;

    }

    public void run() {

        String text = "Apply current channel names, colors and ranges to all images?\n(Will apply on images with the same image type and number of channels)";
        text += "\nNOTE: this will only work if you also change the channel names!";
        if (!Dialogs.showConfirmDialog("Apply Display Settings to Project", text))
            return;

        ImageData<BufferedImage> currentImageData = qupath.getImageData();

        ImageServer<BufferedImage> currentServer = currentImageData.getServer();

        List<ImageChannel> channels = currentImageData.getServer().getMetadata().getChannels();

        List<String> channel_names = channels.stream().map(ImageChannel::getName).collect(Collectors.toList());

        // Try to get an existing display if the image is currently open
        QuPathViewer viewer = qupath.getAllViewers().stream()
                .filter(v -> v.getImageData() == currentImageData)
                .findFirst()
                .orElse(null);
        ImageDisplay display = null;
        if( viewer == null) {
            display = new ImageDisplay();
            try {
                display.setImageData(currentImageData, false);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            viewer.getImageDisplay();
        }
        var available = display.availableChannels();

        List<Float> channel_min = available.stream().map(ChannelDisplayInfo::getMinDisplay).collect(Collectors.toList());
        List<Float> channel_max = available.stream().map(ChannelDisplayInfo::getMaxDisplay).collect(Collectors.toList());
        List<Integer> channel_colors = available.stream().map(ChannelDisplayInfo::getColor).collect(Collectors.toList());

        // Get all images from Project

        List<ProjectImageEntry<BufferedImage>> imageList = qupath.getProject().getImageList();

        AtomicInteger nImages = new AtomicInteger();
        AtomicInteger nIgnored = new AtomicInteger();
        imageList.stream().parallel().forEach(entry -> {
            try {
                ImageData<BufferedImage> imageData = entry.readImageData();

                ImageServer<BufferedImage> server = entry.getServerBuilder().build();

                //logger.debug("Ref Type: {} vs Current Type {}", currentImageData.getImageType(), imageData.getImageType());
                //logger.debug("Ref nC: {} vs Current nC {}", currentServer.getMetadata().getSizeC(), server.getMetadata().getSizeC());

                if (currentImageData.getImageType().equals(imageData.getImageType()) && currentServer.getMetadata().getSizeC() == server.getMetadata().getSizeC()) {
                    nImages.getAndIncrement();

                    // Set the names
                    QPEx.setChannelNames(imageData, channel_names.toArray(new String[0]));

                    QPEx.setChannelColors(imageData, channel_colors.toArray(new Integer[0]));

                    for (int i = 0; i < channel_min.size(); i++) {
                        QPEx.setChannelDisplayRange(imageData, channel_names.get(i), channel_min.get(i), channel_max.get(i));
                    }

                    // Update the thumbnail
                    BufferedImage thumbnail = ProjectCommands.getThumbnailRGB(imageData.getServer());
                    entry.setThumbnail(thumbnail);
                    entry.saveImageData(imageData);


                    logger.info("Image {} updated.", entry.getImageName());

                } else {
                    int i = nIgnored.getAndIncrement();
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        });

        try {
            qupath.getProject().syncChanges();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Dialogs.showConfirmDialog("Applied settings to "+nImages.get() +" images", ""+nIgnored.get()+" images were ignored due to either wrong image type or channel number");
    }
}