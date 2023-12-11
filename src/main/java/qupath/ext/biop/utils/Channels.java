package qupath.ext.biop.utils;

import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Channels class to handle loading and saving channel settings
 * It can modify channels based on either a simple text file or a string of the type
 * position, name, color, minDisplay, maxDisplay
 * eg.
 * <pre>1, First, #0300ff, 0, 100
 * 3, Third, #ff3300, 10, 1200</pre>
 *
 * Also includes the possibility to save the settings to a textfile in the above format.
 *
 * @author Olivier Burri
 */
public class Channels {

    private static final Logger logger = LoggerFactory.getLogger(Channels.class);

    /**
     * Set the channels directly from a text file
     * @param settingsFile the file containing the channel settings, generally produced using {@link Channels#writeChannelSettings(List, File)}
     */
    public static void setChannelSettings(File settingsFile) {
        try {
            List<Channel> settings = readChannelSettings(settingsFile);
            setChannelSettings(settings);
        } catch (FileNotFoundException e) {
            logger.warn("Could not set channel settings:\n{}", e.getMessage());
        }
    }

    /**
     * This method actually sets the channel displays based on the channels list.
     * @param channels the list of channels, created with {@link Channels#readChannelSettings(File)}, {@link Channels#getCurrentChannelSettings()} or {@link Channels#readChannelSettings(String)}
     */
    public static void setChannelSettings(List<Channel> channels) {
        ImageData<BufferedImage> imageData = QuPathGUI.getInstance().getImageData();

        // Ready to set all the things
        int nChannels = imageData.getServer().nChannels();
        List<String> names = IntStream.rangeClosed(1, nChannels).mapToObj(idx -> {
            Optional<Channel> channel = channels.stream().filter(c -> c.position == idx).findFirst();
            return channel.isPresent() ? channel.get().name : null;
        }).collect(Collectors.toList());

        List<Integer> colors = IntStream.rangeClosed(1, nChannels).mapToObj(idx -> {
            Optional<Channel> channel = channels.stream().filter(c -> c.position == idx).findFirst();
            return channel.isPresent() ? channel.get().getQuPathColor() : null;
        }).collect(Collectors.toList());

        QPEx.setChannelNames(imageData, names.toArray(new String[0]));
        QPEx.setChannelColors(imageData, colors.toArray(new Integer[0]));

        channels.stream().forEach(c -> QPEx.setChannelDisplayRange(c.position - 1, c.displayMin, c.displayMax));

        // Set visibility... Though this is only for the current display
        QuPathViewer viewer = QuPathGUI.getInstance().getViewer();
        ObservableList<ChannelDisplayInfo> availableChannels = viewer.getImageDisplay().availableChannels();

        AtomicInteger position = new AtomicInteger(1);
        availableChannels.stream().forEach(channelInfo -> {
            // Check that the channel is in our channel list, based on index
            int pos = position.getAndIncrement();
            boolean active = channels.stream().filter(c -> c.position == pos).findFirst().isPresent();
            viewer.getImageDisplay().setChannelSelected(channelInfo, active);
        });
        viewer.repaintEntireImage();
    }

    /**
     * Return the current channel names, colors and minMax displays to a list
     * NOTE: Channels that are not currently selected will not be sent
     * @return a list that can be used with {@link Channels#writeChannelSettings(List, File) or {@link Channels#setChannelSettings(List)}}
     */
    public static List<Channel> getCurrentChannelSettings() {
        // This will only work and do things on fluorescent data
        ImageData<BufferedImage> imageData = QuPathGUI.getInstance().getImageData();
        if (!imageData.isFluorescence()) return Collections.emptyList();

        QuPathViewer viewer = QuPathGUI.getInstance().getViewer();
        ObservableList<ChannelDisplayInfo> availableChannels = viewer.getImageDisplay().availableChannels();
        ObservableList<ChannelDisplayInfo> selectedChannels = viewer.getImageDisplay().selectedChannels();

        AtomicInteger position = new AtomicInteger(1);
        List<Channel> channels = availableChannels.stream().map(c -> {
            int p = position.getAndIncrement();
            String name = c.getName();

            String color = "#" + Integer.toHexString(Color.decode(c.getColor().toString()).getRGB()).substring(2);
            int displayMin = (int) c.getMinDisplay();
            int displayMax = (int) c.getMaxDisplay();

            // Is this channel visible?
            if (selectedChannels.contains(c)) {
                return new Channel(p, name, color, displayMin, displayMax);
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
        return channels;
    }

    /**
     * Read the parameters of a simple string with each line formatted as:
     * position, name, color, minDisplay, maxDisplay
     * eg.
     * <pre>1, One channel, #0300ff, 0, 100
     * 3, Another channel, #ff3300, 10, 1200</pre>
     * @param settingsString a manually created String
     * @return a list that can be used with {@link Channels#writeChannelSettings(List, File) or {@link Channels#setChannelSettings(List)}}
     */
    public static List<Channel> readChannelSettings(String settingsString) {
        List<Channel> channels = new ArrayList<>();
        String[] settings = settingsString.split("\n");
        for (String setting : settings) {
            Channel c = Channel.getChannel(setting);
            if (c != null)
                channels.add(Channel.getChannel(setting));
        }
        return channels;
    }

    /**
     * Read the settings from a simple text file, generally made using {@link Channels#writeChannelSettings(List, File)}
     * but could also be made manually
     * @param settingsFile the file with the settings.
     * @return a list that can be used with {@link Channels#writeChannelSettings(List, File) or {@link Channels#setChannelSettings(List)}}
     * @throws FileNotFoundException in case the file does not exist
     */
    public static List<Channel> readChannelSettings(File settingsFile) throws FileNotFoundException {
        List<Channel> channels = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(settingsFile))) {

            br.lines().forEach(line -> {
                Channel c = Channel.getChannel(line);
                if (c != null)
                    channels.add(Channel.getChannel(line));
            });

            return channels;
        } catch (IOException e) {
            logger.warn("Error while reading channel settings:\n{}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Write the provided Channels to a file
      * @param channelSettings settings, obtained using {@link Channels#readChannelSettings(String)}, {@link Channels#readChannelSettings(File)} or {@link Channels#getCurrentChannelSettings()}
     * @param settingsFile a file that will be created or overwritten with the settings converted to text.
     * @throws IOException in case the file cannot be written
     */
    public static void writeChannelSettings(List<Channel> channelSettings, File settingsFile) throws IOException {

        try (PrintWriter writer = new PrintWriter(new FileWriter(settingsFile))) {

            writer.print("");
            channelSettings.stream().forEach(c -> {
                writer.println(c.toString());
            });
        }
    }

    /**
     * Private Channel class that handles converting to and from text
     */
    private static class Channel {
        int position;
        String name;
        String color;
        int displayMin;
        int displayMax;

        // Magic pattern to remove the blablabla (C#) bit"
        static Pattern channelNamePattern = Pattern.compile("(.*) \\(C\\d+\\)");

        // Builder so we can use the 'as' magic
        public Channel(int position, String name, String color, int displayMin, int displayMax) {
            this.position = position;

            // Cleanup channel name ( remove '(C#)' at the end if present)
            Matcher m = channelNamePattern.matcher(name);
            if (m.matches()) {
                name = m.group(1);
            }

            this.name = name;
            this.color = color;
            this.displayMin = displayMin;
            this.displayMax = displayMax;
        }

        // This parses the channel String from a file
        private static Channel getChannel(String channelString) {
            // Trim and remove newlines
            List<String> elements = Arrays.stream(channelString.trim().split(",")).map(String::trim).collect(Collectors.toList());

            try {
                int position = Integer.parseInt(elements.get(0));
                String name = elements.get(1);
                String color = elements.get(2);
                int displayMin = Integer.parseInt(elements.get(3));
                int displayMax = Integer.parseInt(elements.get(4));

                return new Channel(position, name, color, displayMin, displayMax);
            } catch (NumberFormatException error) {
                logger.warn("Error parsing channel string\n{}", error.getMessage());
                return null;
            }
        }

        // Convert a text or hex color to a QuPath color
        private int getQuPathColor() {
            Color c;
            try {
                // get color by hex or octal value
                c = Color.decode(this.color);
            } catch (NumberFormatException nfe) {
                // if we can't decode lets try to get it by name
                try {
                    // try to get a color by name using reflection
                    final Field f = Color.class.getField(this.color.toLowerCase());

                    c = (Color) f.get(null);
                } catch (Exception ce) {
                    // if we can't get any color return white
                    logger.warn("Could not get color for Channel '{}'. Defaulting to grey", this.name);
                    c = Color.white;
                }
            }

            // Convert to QuPath Color
            return QPEx.makeRGB(c.getRed(), c.getGreen(), c.getBlue());
        }

        // This is used to print out the contents of a Channel and for writing to a file
        public String toString() {
            return String.format("%d, %s, %s, %d, %d", this.position, this.name, this.color, this.displayMin, this.displayMax);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Channel channel = (Channel) o;
            return position == channel.position && displayMin == channel.displayMin && displayMax == channel.displayMax && Objects.equals(name, channel.name) && Objects.equals(color, channel.color);
        }

        @Override
        public int hashCode() {
            return Objects.hash(position, name, color, displayMin, displayMax);
        }
    }
}