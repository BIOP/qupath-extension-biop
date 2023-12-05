import qupath.ext.biop.cellpose.Cellpose2D
// For all the options from cellpose: https://cellpose.readthedocs.io/en/latest/cli.html
// For all the options from omnipose: https://omnipose.readthedocs.io/command.html#all-options

// Specify the model name (cyto, nuc, cyto2, omni_bact or a path to your custom model)
def pathModel = 'bact_phase_omni'
def cellpose = Cellpose2D.builder( pathModel )
//        .pixelSize( 0.5 )                  // Resolution for detection in um
//        .channels( 'DAPI' )	               // Select detection channel(s)
        .preprocess( ImageOps.Filters.median(2) )                // List of preprocessing ImageOps to run on the images before exporting them
//        .normalizePercentilesGlobal(0.1, 99.8, 10) // Convenience global percentile normalization. arguments are percentileMin, percentileMax, dowsample.
//        .tileSize(1024)                  // If your GPU can take it, make larger tiles to process fewer of them. Useful for Omnipose
//        .cellposeChannels(1,2)           // Overwrites the logic of this plugin with these two values. These will be sent directly to --chan and --chan2
//        .cellprobThreshold(0.0)          // Threshold for the mask detection, defaults to 0.0
//        .flowThreshold(0.4)              // Threshold for the flows, defaults to 0.4 
        .diameter(30)                    // Median object diameter. Set to 0.0 for the `bact_omni` model or for automatic computation
        .useOmnipose()                   // Use the omnipose instead
        .addParameter("cluster")         // Any parameter from cellpose or omnipose not available in the builder. 
//        .addParameter("save_flows")      // Any parameter from cellpose or omnipose not available in the builder.
//        .addParameter("anisotropy", "3") // Any parameter from cellpose or omnipose not available in the builder.
//        .cellExpansion(5.0)              // Approximate cells based upon nucleus expansion
//        .cellConstrainScale(1.5)         // Constrain cell expansion using nucleus size
//        .classify("My Detections")       // PathClass to give newly created objects
        .measureShape()                  // Add shape measurements
        .measureIntensity()              // Add cell measurements (in all compartments)  
//        .createAnnotations()             // Make annotations instead of detections. This ignores cellExpansion
        .simplify(0)                     // Simplification 1.6 by default, set to 0 to get the cellpose masks as precisely as possible
        .build()

// Run detection for the selected objects
def imageData = getCurrentImageData()
def pathObjects = getSelectedObjects()
if (pathObjects.isEmpty()) {
    Dialogs.showErrorMessage("Cellpose", "Please select a parent object!")
    return
}
cellpose.detectObjects(imageData, pathObjects)
println 'Done!'