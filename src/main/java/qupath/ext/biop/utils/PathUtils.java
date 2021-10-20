package qupath.ext.biop.utils;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.plugins.objects.DilateAnnotationPlugin;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.QP;

import java.util.ArrayList;
import java.util.List;

public class PathUtils {
    private final static Logger logger = LoggerFactory.getLogger(PathUtils.class);

    /**
     * returns a rectangle with teh whole dataset as an annotation. It does not add it to the Hierarchy
     *
     * @return an Annotation Object with the whole image
     */
    public static PathObject getFullImageAnnotation() {
        ImageData<?> imageData = QP.getCurrentImageData();
        if (imageData == null)
            return null;
        ImageServer<?> server = imageData.getServer();
        return qupath.lib.objects.PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, server.getWidth(), server.getHeight(), null));
    }

    /**
     * returns the area of the current PathObject in calibrated units.
     *
     * @param object the object to try and compute the area from
     * @return the calibrated area in um2
     */
    public static double getAreaMicrons(PathObject object) {
        double pixel_size = QP.getCurrentServer().getPixelCalibration().getAveragedPixelSizeMicrons();
        double area = getArea(object);
        return area * pixel_size * pixel_size;
    }


    /**
     * returns the area of the current PathObject in pixels If the area is not defined (like points) it returns 0
     *
     * @param object the object to try and compute the area from
     * @return the area in px2
     */
    public static double getArea(PathObject object) {
        ROI roi = object.getROI();
        return roi.getArea();
    }

    /**
     * expand, taken from {@link DilateAnnotationPlugin} which returns an PathObject expanded or dilated by the given amount in pixels
     *
     * @param pathObject        the PathObject to dilate or erode
     * @param radiusPixels      the radius of erosion/dilation
     * @param removeInterior    whether to create an annotation from the difference of the two or just a full new annotation (kind of like "make band"
     * @param constrainToParent limits the expansion to the parent shape. pathObject must be a child of something in the image.
     * @return an Annotation or Detection object of the same class
     */
    public static PathObject expand(final PathObject pathObject, final double radiusPixels, final boolean removeInterior, final boolean constrainToParent) {

        ROI roi = pathObject.getROI();

        Geometry geometry = roi.getGeometry();

        int capVal = BufferParameters.CAP_ROUND;

        Geometry geometry2 = BufferOp.bufferOp(geometry, radiusPixels, BufferParameters.DEFAULT_QUADRANT_SEGMENTS, capVal);

        // If the radius is negative (i.e. an erosion), then the parent will be the original object itself
        boolean isErosion = radiusPixels < 0;
        PathObject parent = isErosion ? pathObject : pathObject.getParent();
        // get bounds just in case
        if (constrainToParent && !isErosion) {
            Geometry parentShape = null;
            if (parent == null || parent.getROI() == null) {
                PathObject annotation = getFullImageAnnotation();

                if(annotation != null) {
                    ROI bounds = annotation.getROI();
                    parentShape = ROIs.createRectangleROI(bounds.getBoundsX(), bounds.getBoundsY(), bounds.getBoundsWidth(), bounds.getBoundsHeight(), ImagePlane.getPlane(roi)).getGeometry();
                }
            } else {
                parentShape = parent.getROI().getGeometry();
            }
            geometry2 = geometry2.intersection(parentShape);
        }

        if (removeInterior) {
            // Difference isn't supported for GeometryCollections
            if (isErosion) {
                geometry = GeometryTools.homogenizeGeometryCollection(geometry);
                geometry2 = geometry.difference(geometry2);
            } else {
                if (geometry.getArea() == 0.0)
                    geometry = geometry.buffer(0.5);
                geometry2 = GeometryTools.homogenizeGeometryCollection(geometry2);
                geometry = GeometryTools.homogenizeGeometryCollection(geometry);
                geometry2 = geometry2.difference(geometry);
            }
        }

        ROI roi2 = GeometryTools.geometryToROI(geometry2, ImagePlane.getPlane(roi));

        if (roi2.isEmpty()) {
            logger.debug("Updated ROI is empty after {} px expansion", radiusPixels);
            return null;
        }

        PathObject newObject;
        // Create a new annotation, with properties based on the original
        if (pathObject instanceof PathAnnotationObject) {
            newObject = qupath.lib.objects.PathObjects.createAnnotationObject(roi2, pathObject.getPathClass());
        } else {
            newObject = qupath.lib.objects.PathObjects.createDetectionObject(roi2, pathObject.getPathClass());
        }

        newObject.setName(pathObject.getName());
        newObject.setColorRGB(pathObject.getColorRGB());

        return newObject;

    }

    /**
     * Merge all PathObjects into a single PathObject
     *
     * @param pathObjects the things to merge. The resulting PathObject will be of the type (Annotation/Detection), color and class of the first PathObject in this list.
     * @return a single object that is the merge of all objects in the list.
     */
    public static PathObject merge(List<PathObject> pathObjects) {
        // Get all the selected annotations with area
        ROI shapeNew = null;
        List<PathObject> children = new ArrayList<>();
        for (PathObject child : pathObjects) {
            if (shapeNew == null)
                shapeNew = child.getROI();//.duplicate();
            else
                shapeNew = RoiTools.combineROIs(shapeNew, child.getROI(), RoiTools.CombineOp.ADD);
            children.add(child);
        }
        // Check if we actually merged anything
        if (children.isEmpty())
            return null;
        if (children.size() == 1)
            return children.get(0);

        // Create and add the new object, removing the old ones

        PathObject pathObjectNew;

        if (pathObjects.get(0) instanceof PathDetectionObject) {
            pathObjectNew = qupath.lib.objects.PathObjects.createDetectionObject(shapeNew);
        } else {
            pathObjectNew = qupath.lib.objects.PathObjects.createAnnotationObject(shapeNew);
        }

        pathObjectNew.setPathClass(pathObjects.get(0).getPathClass());
        pathObjectNew.setColorRGB(pathObjects.get(0).getColorRGB());

        return pathObjectNew;
    }

}
