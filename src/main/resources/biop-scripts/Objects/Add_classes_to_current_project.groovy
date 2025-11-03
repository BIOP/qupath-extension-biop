/**
 * Find all classifications of detections/annotations and add them to the project if they don't exist
 *  
 * author: Rémy Dornier - PTBIOP
 * date: 2025-11-03
 * version: 1.0.0
 * 
 * Last tested on QuPath 0.6.0
 * 
 */


/***********************
 * VARIABLES TO MODIFY
 ***********************/

def getClassesFromAnnotations = true
def getClassesFromDetections = true


/***********************
 * BEGINNING OF THE SCRIPT
 ***********************/


def classifications = []

// get the classes from detections
if(getClassesFromDetections) {
    println "Getting classes from detections" 
    classifications.addAll(getDetectionObjects().collect {it?.getPathClass()} as Set)
}

// get the classes from annotations
if(getClassesFromAnnotations) {
    println "Getting classes from annotations" 
    classifications.addAll(getAnnotationObjects().collect {it?.getPathClass()} as Set)
}

if(!classifications.isEmpty()){
    Platform.runLater(() -> {
        // get the available classes of the project
        def availablePathClasses = QPEx.getQuPath().getAvailablePathClasses();
        def newPathClasses = []
        newPathClasses.addAll(classifications)
        
        for(def cellPathClass : newPathClasses) {
            // create new pathClasses if they doesn't exist 
            if (cellPathClass != null && !availablePathClasses.contains(cellPathClass)) {
                availablePathClasses.add(cellPathClass);
            }
        }
        
        // set the project classes
        QPEx.getQuPath().getProject().setPathClasses(availablePathClasses);
    })
}

println "End of the script"
return


/* ****************
 * IMPORTS
 * ****************/ 

import qupath.lib.gui.scripting.QPEx;
