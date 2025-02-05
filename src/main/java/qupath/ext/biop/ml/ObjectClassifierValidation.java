package qupath.ext.biop.ml;

import ij.measure.ResultsTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ObjectClassifierValidation {
    private static final Logger logger = LoggerFactory.getLogger(ObjectClassifierValidation.class);
    private QuPathGUI qupath;
    private Project<BufferedImage> project;

    private MatchList matches;
    private List<PathClass> groundTruthClasses;

    public ObjectClassifierValidation(Project<BufferedImage>project) {
        this.project = project;
        this.qupath = QuPathGUI.getInstance();
    }

    private ObjectClassifier<BufferedImage> classifier;

    public void computeValidation(ObjectClassifier<BufferedImage> classifier, String metadataKeyFilter, String metadataValueFilter) {
        // If nothing was set, figure out the classes here
        // Get the classifier
        this.classifier = classifier;
        List<ProjectImageEntry<BufferedImage>> selectedEntries = project.getImageList().stream()
                .filter(entry -> {
                    if (entry.getMetadataMap().containsKey(metadataKeyFilter)) {
                        return entry.getMetadataMap().get(metadataKeyFilter).equals(metadataValueFilter);
                    }
                    return false;
                }).collect(Collectors.toList());

        Set<PathClass> classSet = selectedEntries.stream()
                .flatMap(entry -> {
                    try {
                        return entry.readHierarchy().getAnnotationObjects().stream()
                                .filter(PathObjectTools::hasPointROI)
                                .toList().stream();
                    } catch (IOException e) {
                        logger.warn("Could not read hierarchy for {}: {}", entry.getImageName(), e.getLocalizedMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .map(PathObject::getPathClass).collect(Collectors.toSet());

        this.groundTruthClasses = classSet.stream().filter(Objects::nonNull).sorted(Comparator.comparing(PathClass::getName)).toList();


        // For each entry
        matches = new MatchList();

        selectedEntries.forEach( entry -> {
            try {
                Collection<PathObject> detections = entry.readHierarchy().getDetectionObjects();
                Collection<PathObject> points = entry.readHierarchy().getAnnotationObjects().stream()
                        .filter(PathObjectTools::hasPointROI)
                        .toList();

                ImageData<BufferedImage> imageData = entry.readImageData();

                // Classify the detections
                classifier.classifyObjects(imageData, detections, true);
                // Find the class of the point inside the detection (if any)
                points.forEach(pointObject -> {
                    detections.forEach(detection -> {
                        pointObject.getROI().getAllPoints().forEach(p -> {
                            if (detection.getROI().contains(p.getX(), p.getY())) {
                                if (groundTruthClasses.contains(pointObject.getPathClass()) && groundTruthClasses.contains(detection.getPathClass())) {
                                    // Now do the thing. Store it
                                    matches.incrementEntry(entry, pointObject.getPathClass(), detection.getPathClass());
                                }
                            }
                        });
                    });
                });
            } catch (IOException e) {
                logger.warn("Could not read hierarchy for {}: {}", entry.getImageName(), e.getLocalizedMessage());
            }
        });
    }

    public void show() {
        this.matches.showMatches( this.groundTruthClasses );
    }

    private class MatchList {
        Map<ProjectImageEntry<BufferedImage>, Map<PathClass, Map<PathClass, Integer>>> matchData;

        MatchList() {
            matchData = new HashMap<>();
        }

        public void incrementEntry( ProjectImageEntry<BufferedImage> image, PathClass gtClass, PathClass predClass ) {
            // Get or create the first-level map for the image
            matchData.computeIfAbsent(image, k -> new HashMap<>())
                    // Get or create the second-level map for key1
                    .computeIfAbsent(gtClass, k -> new HashMap<>())
                    .merge(predClass, 1, Integer::sum);
        }

        // Sum all values across all images for a given key1 and key2 pair
        public int sumAcrossEntries(PathClass gtClass, PathClass predClass) {
            int totalSum = 0;

            // Iterate through each image
            for (Map<PathClass, Map<PathClass, Integer>> gtMap : matchData.values()) {
                // Check if the image contains the specified key1
                if (gtMap.containsKey(gtClass)) {
                    // Check if the key1 contains the specified key2 and sum the value
                    Map<PathClass, Integer> predMap = gtMap.get(gtClass);
                    totalSum += predMap.getOrDefault(predClass, 0);
                }
            }
            return totalSum;
        }

        public int getTP( ProjectImageEntry<BufferedImage> image, PathClass gtClass ) {
            return this.matchData.get(image).get(gtClass).getOrDefault(gtClass, 0);
        }

        public int getFN( ProjectImageEntry<BufferedImage> image, PathClass gtClass ) {
            return this.matchData.get(image).get(gtClass).values().stream().mapToInt(Integer::intValue).sum() - getTP(image, gtClass);
        }

        public int getFP( ProjectImageEntry<BufferedImage> image, PathClass gtClass ) {
            return this.matchData.get(image).values().stream().mapToInt( t -> t.getOrDefault(gtClass, 0) ).sum() - getTP(image, gtClass);
        }

        private void showMatches( List<PathClass> gtClasses ) {
            ResultsTable rt = new ResultsTable();
            rt.setValue("", 0, "");
            for (int i = 0; i < gtClasses.size(); i++) {
                rt.setValue("", i, "Predicted - " + gtClasses.get(i).getName());
                rt.setValue("GT - " + gtClasses.get(i).getName(), 0, 0);
            }

            for (Map<PathClass, Map<PathClass, Integer>> gtMap : matchData.values()) {

                gtClasses.forEach(gt -> {
                    Map<PathClass, Integer> predMap = gtMap.computeIfAbsent(gt, k -> new HashMap<>());
                    predMap.forEach((predK, value) -> {
                        int rowIdx = gtClasses.indexOf(predK);

                        if (rowIdx >= 0) {
                            int val = (int) rt.getValue("GT - " + gt.getName(), rowIdx);
                            rt.setValue("GT - " + gt.getName(), rowIdx, value + val);
                        }
                    });
                });
            }
            rt.show( classifier.toString() + " - Matches" );

            // Make a table for each image to contain all the fancy calculations
            ResultsTable perImage = new ResultsTable();

            matchData.keySet().stream().sorted(Comparator.comparing(ProjectImageEntry::getID)).forEach( entry -> {
                perImage.incrementCounter();
                perImage.addValue("Entry Name", entry.getImageName());

                int tp=0, fp=0, fn=0 ;

                for (PathClass groundTruthClass : groundTruthClasses) {
                    tp += getTP(entry, groundTruthClass);
                    fp += getFP(entry, groundTruthClass);
                    fn += getFN(entry, groundTruthClass);
                }

                double precision = (double) tp / ( tp + fp );
                double recall = (double) tp / ( tp + fn );
                double f1 = ( 2 * precision * recall ) / ( precision + recall );


                perImage.addValue("TP", tp);
                perImage.addValue("FP", fp);
                perImage.addValue("FN", fn);
                perImage.addValue("Precision", precision);
                perImage.addValue("Recall", recall);
                perImage.addValue("F1 Score", f1);
            });
            perImage.show( classifier.toString() + " - Per Image");
        }
    }
}
