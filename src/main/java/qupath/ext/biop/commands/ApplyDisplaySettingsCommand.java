package qupath.ext.biop.commands;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerPlus;
import qupath.lib.gui.viewer.ViewerPlusDisplayOptions;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ApplyDisplaySettingsCommand implements Runnable {


    final static Logger logger = LoggerFactory.getLogger(ImageDisplay.class);
    private Stage stage;
    private DoubleProperty progressProperty;
    private StringProperty processingProperty;
    private StringProperty imageNameProperty;

    private BooleanProperty doThumbnails;
    private BooleanProperty isRunningProperty = new javafx.beans.property.SimpleBooleanProperty(false);
    private BooleanProperty cancelProperty = new javafx.beans.property.SimpleBooleanProperty(false);

    private QuPathGUI qupath;
    private ExecutorService executor;

    //ApplyDisplaySettingsCommand
    public ApplyDisplaySettingsCommand(final QuPathGUI qupath) {

        this.qupath = qupath;
    }

    private void init() {

        executor = qupath.getThreadPoolManager().getSingleThreadExecutor(this);


        // Build a simple GUI
        Label labInfo = new Label("Apply channel names, colors and ranges from this image to all images?\n(Will apply to images with the same image type and number of channels)");
        labInfo.setWrapText(false);

        Label labProcessing = new Label("");
        processingProperty = labProcessing.textProperty();

        Label labImageName = new Label("");
        imageNameProperty = labImageName.textProperty();
        labImageName.setStyle("-fx-font-weight: bold");
        CheckBox cbThumbnail = new CheckBox("Update thumbnails (Slow)");
        doThumbnails = cbThumbnail.selectedProperty();

        HBox processing = new HBox();
        processing.setSpacing(5);
        processing.getChildren().add(labProcessing);
        processing.getChildren().add(labImageName);

        ProgressBar pbProgress = new ProgressBar(0.0);
        pbProgress.setMaxWidth(Double.MAX_VALUE);
        progressProperty = pbProgress.progressProperty();

        Button applyBtn = new Button("Apply to project");
        applyBtn.disableProperty().bind(isRunningProperty);
        applyBtn.setOnAction(event -> {
            cancelProperty.set(false);
            progressProperty.set(0.0);
            processingProperty.set("Processing");
            imageNameProperty.set("");
            applySettings();
        });
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(event -> {
            cancelProperty.set(true);
        });

        VBox pane = new VBox();
        pane.setSpacing(5);
        pane.setPadding(new Insets(20));

        pane.getChildren().add(labInfo);
        pane.getChildren().add(cbThumbnail);
        pane.getChildren().add(processing);
        pane.getChildren().add(pbProgress);
        pane.getChildren().add(applyBtn);

        stage = new Stage();
        stage.initOwner(qupath.getStage());
        stage.setTitle("Apply display settings");
        stage.setScene(new Scene(pane));

        stage.setOnCloseRequest(event -> {
            if( isRunningProperty.get()) {
                Dialogs.showErrorNotification("Display settings command canceled", "Apply display settings command canceled while running.");
                cancelProperty.set(true);
            }
            stage.close();
            event.consume();
        });
    }

    public void run() {
        init();
        stage.show();
    }
    public void applySettings() {
        isRunningProperty.set(true);

        ImageData<BufferedImage> currentImageData = qupath.getImageData();

        ImageServer<BufferedImage> currentServer = currentImageData.getServer();

        List<ImageChannel> channels = currentImageData.getServer().getMetadata().getChannels();

        List<String> channel_names = channels.stream().map(ImageChannel::getName).collect(Collectors.toList());

        // Try to get an existing display if the image is currently open
        QuPathViewer viewer = qupath.getAllViewers().stream()
                .filter(v -> v.getImageData() == currentImageData)
                .findFirst()
                .orElse(null);
        ImageDisplay display;
        if( viewer == null) {
            display = new ImageDisplay();
            try {
                display.setImageData(currentImageData, false);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            display = viewer.getImageDisplay();
        }

        // Build all the information we need to apply the settings
        var available = display.availableChannels();
        List<Float> channel_min = available.stream().map(ChannelDisplayInfo::getMinDisplay).collect(Collectors.toList());
        List<Float> channel_max = available.stream().map(ChannelDisplayInfo::getMaxDisplay).collect(Collectors.toList());
        List<Integer> channel_colors = available.stream().map(ChannelDisplayInfo::getColor).collect(Collectors.toList());

        // Also make sure that this command selects the channels that are currently displayed for all entries, so
        // we need those as well
        ObservableList<ChannelDisplayInfo> selectedChannels = display.selectedChannels();

        // Get all images from Project
        List<ProjectImageEntry<BufferedImage>> imageList = qupath.getProject().getImageList();

        // Counters for giving a summary of the processing
        AtomicInteger nTotal = new AtomicInteger(0);
        AtomicInteger nProcessed = new AtomicInteger(0);
        AtomicInteger nIgnored = new AtomicInteger(0);
        int totalImages = imageList.size();

        executor.submit(() -> {

            imageList.stream().parallel().forEach(entry -> {

                // If the cancel button was pressed, stop processing
                if (cancelProperty.get())
                    return;
                try {


                    ImageData<BufferedImage> imageData = entry.readImageData();

                    // To know the number of channels, we need to build the server
                    ImageServer<BufferedImage> server = entry.getServerBuilder().build();

                    // Check if the image type and number of channels match
                    if (currentImageData.getImageType().equals(imageData.getImageType()) &&
                        currentServer.getMetadata().getSizeC() == server.getMetadata().getSizeC()) {

                        nProcessed.getAndIncrement();

                        // Required for the channel color to be updated in case we do not change the names!
                        imageData.removeProperty(ImageDisplay.class.getName());

                        // Set the names
                        QPEx.setChannelNames(imageData, channel_names.toArray(new String[0]));

                        // Set the colors
                        QPEx.setChannelColors(imageData, channel_colors.toArray(new Integer[0]));

                        // Setting the Display range works only per channel unlike the other two above
                        for (int i = 0; i < channel_min.size(); i++) {
                            QPEx.setChannelDisplayRange(imageData, channel_names.get(i), channel_min.get(i), channel_max.get(i));
                        }

                        // Set the selected channels as active or not active
                        ImageDisplay tempDisplay = ImageDisplay.create(imageData);

                        // Start by setting them all to unselected
                        for (ChannelDisplayInfo channel : tempDisplay.availableChannels()) {
                            tempDisplay.setChannelSelected(channel, false);
                        }
                        // Then set the ones that should be selected
                        for (ChannelDisplayInfo channel : tempDisplay.availableChannels()) {
                            for (ChannelDisplayInfo selected : selectedChannels) {
                                if (selected.getName().equals(channel.getName())) {
                                    tempDisplay.setChannelSelected(channel, true);
                                }
                            }
                        }

                        // Update the thumbnail. As this is related to the viewer display settings we might need to create a new viewer?
                        if (doThumbnails.get()) {
                            QuPathViewerPlus tempViewer = new QuPathViewerPlus(qupath.getImageRegionStore(), new OverlayOptions(), new ViewerPlusDisplayOptions());
                            tempViewer.setImageData(imageData);
                            BufferedImage thumbnail = tempViewer.getRGBThumbnail();
                            entry.setThumbnail(thumbnail);
                            tempViewer.closeViewer();
                        }

                        // Save our hard labor
                        entry.saveImageData(imageData);
                        logger.debug("Image {} updated.", entry.getImageName());

                    } else {
                        // This image does not match the current image type or number of channels
                        nIgnored.getAndIncrement();
                    }
                    nTotal.getAndIncrement();
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                } finally {
                    // at each run update the progress bar
                    Platform.runLater(() -> {
                        progressProperty.set(nTotal.get() / (double) totalImages);
                        imageNameProperty.set(entry.getImageName());
                    });
                }

            });
            try {
                qupath.getProject().syncChanges();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }

            Dialogs.showInfoNotification("Applied settings to "+nProcessed.get() +" images", ""+nIgnored.get()+" images were ignored due to either wrong image type or channel number");
            isRunningProperty.set(false);

        });


    }
}