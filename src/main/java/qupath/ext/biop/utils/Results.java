package qupath.ext.biop.utils;

import ij.measure.ResultsTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;
import qupath.lib.scripting.QP;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Convenience class to export results only for the selected objects.
 *
 * @author Olivier Burri
 */

public class Results {
    final public static String um = GeneralTools.micrometerSymbol();
    final private static Logger logger = LoggerFactory.getLogger(Results.class);

    /**
     * By making use of ObservableMeasurementTableData, we can query each result and get a string back Works, for area,
     * PathClasses, parents, and of course any other measurement in the final table
     *
     * @param resultColumns a list of all the results we want to have, exactly the same names as in teh Measurement
     *                      Results tables
     * @param objects       the pathObjects we want to get the measurements from
     * @param resultsFile   the file where this tool should write to. Note that if the file exists, it will be appended
     * @see ObservableMeasurementTableData
     */
    static public void sendResultsToFile(ArrayList<String> resultColumns, ArrayList<PathObject> objects, File resultsFile) {

        // We use a ResultsTable to store the data, and we need to see if it exists so that we can append to it
        ResultsTable results;

        if (resultsFile.exists()) {
            // Try to open the previous results table
            try {
                results = ResultsTable.open(resultsFile.getAbsolutePath());
            } catch (IOException e) {
                logger.error("Could not reopen results file {}, either the file is locked or it is not a results table.", resultsFile.getName());
                results = new ResultsTable();

            }
        } else {
            // New Results Table
            results = new ResultsTable();
        }

        ObservableMeasurementTableData ob = new ObservableMeasurementTableData();
        // This line creates all the measurements
        ob.setImageData(QP.getCurrentImageData(), objects);

        ProjectImageEntry<BufferedImage> entry = QP.getProjectEntry();

        String rawImageName = entry.getImageName();

        // Add value for each selected object
        for (PathObject pathObject : objects) {
            results.incrementCounter();
            results.addValue("Image Name", rawImageName);

            // Check if image has associated metadata and add it as columns
            if (entry.getMetadataKeys().size() > 0) {
                Collection<String> keys = entry.getMetadataKeys();
                for (String key : keys) {
                    results.addValue("Metadata_" + key, entry.getMetadataValue(key));
                }
            }

            // Then we can add the results the user requested
            // Because the Mu is sometimes poorly formatted, we remove them in favor of a 'u'
            for (String col : resultColumns) {
                if (ob.isNumericMeasurement(col))
                    results.addValue(col.replace(um, "um"), ob.getNumericValue(pathObject, col));
                if (ob.isStringMeasurement(col))
                    results.addValue(col.replace(um, "um"), ob.getStringValue(pathObject, col));
            }
        }
        results.save(resultsFile.getAbsolutePath());
        logger.info("Results {} Saved under {}, contains {} rows", resultsFile.getName(), resultsFile.getParentFile().getAbsolutePath(), results.size());
    }

    static public void sendResultsToFile(ArrayList<String> resultColumns, ArrayList<PathObject> objects) {
        File resultsFolder = new File(Projects.getBaseDirectory(QP.getProject()), "results");
        File resultsFile = new File(resultsFolder, "results.txt");
        if (!resultsFolder.exists()) {
            resultsFolder.mkdirs();
        }
        sendResultsToFile(resultColumns, objects, resultsFile);
    }

    static public void sendResultsToFile(ArrayList<PathObject> objects) {
        ObservableMeasurementTableData resultColumns = getAllMeasurements(objects);

        sendResultsToFile(new ArrayList<>(resultColumns.getAllNames()), objects);
    }

    /**
     * Returns all the measurements available in QuPath for the all pathObjects Then we can use things like
     * getStringValue() and getDoubleValue()
     *
     * @return a class that you can use to access the results
     * @see ObservableMeasurementTableData
     */
    public static ObservableMeasurementTableData getAllMeasurements() {
        PathObjectHierarchy hierarchy = QP.getCurrentHierarchy();
        return getAllMeasurements(hierarchy.getFlattenedObjectList(null));
    }

    /**
     * Creates an ObservableMeasurementTableData for the requested PathObjects
     *
     * @param pathObjects a list of PathObjects to compute measurements from
     * @return an object you can access the results getStringValue() and getDoubleValue()
     * @see ObservableMeasurementTableData
     */
    public static ObservableMeasurementTableData getAllMeasurements(List<PathObject> pathObjects) {
        ObservableMeasurementTableData ob = new ObservableMeasurementTableData();
        // This line creates all the measurements
        ob.setImageData(QP.getCurrentImageData(), pathObjects);
        return ob;
    }

    /**
     * Conveniently add a bunch of metadata to each entry in the project
     *
     * @param project The project to update
     * @param csvFile the CSV file that will be opened by ImageJ's ResultsTable
     * @throws IOException in case the project cannot be refreshed
     */
    public static void addMetadataToProject(Project<BufferedImage> project, File csvFile) throws IOException {
        // Open the file
        ResultsTable metadata = ResultsTable.open(csvFile.getAbsolutePath());
        if (!metadata.columnExists("Image Name")) {
            logger.error("No Column 'Image Name' in csv file {}. Make sure that the column exists and that it matches the name of the entries in your QuPath Project", csvFile.getName());
            return;
        }

        logger.info("Available Columns: {}", metadata.getColumnHeadings());

        List<ProjectImageEntry<BufferedImage>> images = project.getImageList();

        images.forEach(image -> {
            String name = image.getImageName();
            logger.info("Processing {}", name);

            // Check all metadata keys to add to this image
            for (int i = 0; i < metadata.getCounter(); i++) {
                String metadataImageName = metadata.getStringValue("Image Name", i);
                if (name.contains(metadataImageName)) {
                    // Append all columns
                    for (int c = 0; c <= metadata.getLastColumn(); c++) {
                        // Excluse columns withoug names or the Image Name Column
                        if (metadata.getColumnHeading(c) != "" && metadata.getColumnHeading(c) != "Image Name") {
                            String key = metadata.getColumnHeading(c);
                            String value = metadata.getStringValue(c, i);
                            // If the value is empty, then do not add it
                            if (!value.equals(""))
                                image.putMetadataValue(key, value);
                        }
                    }
                }
            }
        });

        try {
            logger.info("Syncing project changes");
            project.syncChanges();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
