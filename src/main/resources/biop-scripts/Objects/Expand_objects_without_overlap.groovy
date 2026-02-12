/**
 * This script expands all annotations by a certain radius in micron, 
 *  
 * author: Rémy Dornier - PTBIOP
 * date: 2026-02-05
 * version: 1.0.0
 * 
 * Last tested on QuPath 0.6.0
 */

 
/**********************
 * VARIABLES TO MODIFY
 *********************/
 
 
double expansion_um = 150      // Expansion distance in microns
boolean processSelectedOnly = false   // true = only selected annotations
int expensionStep = 10


 /***********************
 * BEGINNING OF THE SCRIPT
 ***********************/
 
 
// ------------------------------------------------------------
// IMAGE & CALIBRATION
// ------------------------------------------------------------
def imageData = getCurrentImageData()
def server = imageData.getServer()
def cal = server.getPixelCalibration()

if (!cal.hasPixelSizeMicrons()) {
    throw new IllegalArgumentException("Image is not calibrated in microns.")
}

double pixelSize = cal.getAveragedPixelSizeMicrons()
double expansion_px = expansion_um / pixelSize

println "Expansion distance: ${expansion_um} µm (${expansion_px} px)"

// ------------------------------------------------------------
// GET ANNOTATIONS
// ------------------------------------------------------------
println "Getting annotations..."
def annotations = processSelectedOnly ?
        getSelectedObjects().findAll { it instanceof PathAnnotationObject } :
        getAnnotationObjects()

if (annotations.isEmpty()) {
    Logger.error("No annotations of class 'portal-vein' found.")
    return
}

// ------------------------------------------------------------
// PREPARE GEOMETRIES
// ------------------------------------------------------------
println "Preparing geometries..."
def geomMap = [:]
def expandedAnnotations = []
annotations.eachWithIndex { ann, idx ->
    geomMap[ann] = GeometryTools.roiToGeometry(ann.getROI())
    expandedAnnotations[idx] = ann 
    ann.setName("Annotation "+ idx)
}

// Union of ALL original geometries (used as collision mask)
Geometry allOriginal = geomMap.values().inject(null) { acc, g ->
    acc == null ? g : acc.union(g)
}

// ------------------------------------------------------------
// EXPAND EACH ANNOTATION
// ------------------------------------------------------------
println "Expanding annotations..."
for(int step = expensionStep; step <= expansion_um ; step+=expensionStep){
    for(int i = 0; i < annotations.size(); i++){
        def ann = annotations.get(i)
        try{
            Geometry baseGeom = geomMap[ann]
            Geometry expandedGeom = GeometryTools.roiToGeometry(expandedAnnotations[i].getROI())

            // Smooth outward buffer
            Geometry expanded = baseGeom.buffer(
                    step / pixelSize,
                    8,
                    BufferParameters.CAP_ROUND
            )
        
            // Keep only the outward ring
            Geometry outwardRing = expanded.difference(expandedGeom)
        
            // Block expansion into other annotations
            Geometry blocked = outwardRing.difference(
                    allOriginal.difference(expandedGeom)
            )
        
            // Final geometry
            Geometry finalGeom = expandedGeom.union(blocked)
            
            // update the original union shape
            allOriginal = allOriginal.union(finalGeom)
        
            if (finalGeom.isEmpty()) {
                println "Skipping empty geometry for annotation"
                return
            }
        
            // Convert geometry back to ROI 
            def newROI = GeometryTools.geometryToROI(
                    finalGeom,
                    ann.getROI().getImagePlane()
            )
            expandedAnnotations[i] = PathObjects.createAnnotationObject(newROI)
        
        }catch (Exception e) {
            Logger.error("The annotation "+ann.getName()+": ID "+ann.getID()+" cannot be expanded")
            Logger.error(e.toString())
        }
    }
}

// set name and add objects
expandedAnnotations.eachWithIndex { ann, idx ->
    ann.setName("Annotation " + idx +  " surrounding ")
}

addObjects(expandedAnnotations)
println "End of the script"


/**********************
 * IMPORTS
 *********************/
 
import qupath.lib.roi.GeometryTools
import qupath.lib.roi.interfaces.ROI
import qupath.lib.objects.PathAnnotationObject
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.operation.buffer.BufferParameters