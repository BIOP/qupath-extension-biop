import qupath.ext.biop.BIOPExtension
import java.nio.file.Paths

scriptFolderLocation = new File("D:/Remy/Github-projects/qupath-0.6.0/qupath/qupath-extension-biop/src/main/resources/qupath-scripts")
def scriptMap = [:]
def folderList = [scriptFolderLocation.getName()]
listFiles(scriptFolderLocation, scriptMap, folderList)
def commands = generateJavaCode(scriptMap)
println commands.join("\n")


def generateJavaCode(scriptMap) {
   def listOfCommands = [] 
   
   listOfCommands.add("")
   listOfCommands.add("private static final LinkedHashMap<String, String> SCRIPTS = new LinkedHashMap<>() {{")
   scriptMap.keySet().each {key ->
       listOfCommands.add("    put('"+key.replace("_"," ").split("\\.")[0]+"', '"+scriptMap.get(key)+"/"+key+"');")
   }
   listOfCommands.add("}};")
   listOfCommands.add("")
   
   listOfCommands.add("private static final LinkedHashMap<String, String> MENUS = new LinkedHashMap<>() {{")
   scriptMap.keySet().each {key ->
       listOfCommands.add("    put('"+key.replace("_"," ").split("\\.")[0]+"', '"+scriptMap.get(key).replace(scriptFolderLocation.getName(), "")+"');")
   }
   listOfCommands.add("}};")
   return listOfCommands
}


def listFiles(folder, scriptMap, folderList){
    folder.listFiles().each {
        if(it.isDirectory() && !it.isHidden()) {
            def temp = new ArrayList<>(folderList)
            temp.add(it.getName())
            listFiles(it, scriptMap, temp)
        }else if(it.isFile() && it.getName().endsWith(".groovy")){
            def scriptName = it.getName()
            scriptMap.put(scriptName, folderList.join("/"))
        }
        
    }
}



