/**
 * This script counts the number of the neighbors in the Delaunay graph,
 * within a certain radius.
 *  
 * author: Rémy Dornier - PTBIOP
 * date: 2026-02-11
 * version: 1.0.0
 * 
 * Last tested on QuPath 0.6.0
 * 
 */
 
 
/**********************
 * VARIABLES TO MODIFY
 *********************/


def radius = 20 // in um


/***********************
 * BEGINNING OF THE SCRIPT
 ***********************/
 
 
def detections = getDetectionObjects()

if(detections.isEmpty()) {
   Logger.warn("No detections on the current image ; nothing to do")
   return
}

def px = getCurrentServer().getPixelCalibration().getAveragedPixelSize()
def subDiv = DelaunayTools.createFromCentroids(detections, true)

detections.each{
    def neighbors = subDiv.getFilteredNeighbors(it, DelaunayTools.centroidDistancePredicate(radius / px, true))
    it.measurements.put("Delaunay NN in radius "+radius+" "+GeneralTools.micrometerSymbol(), neighbors.size())    
}


/**********************
 * IMPORTS
 *********************/

import qupath.lib.analysis.DelaunayTools
