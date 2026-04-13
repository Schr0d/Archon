plugins {
    id("java")
}

// Task to copy the canvas-based index.html to archon-viz resources
tasks.register("copyCanvasViewer") {
    group = "archon"
    description = "Copy canvas-based viewer to archon-viz resources"

    val sourceFile = file("index.html")
    val targetDir = file("../archon-viz/src/main/resources")
    val targetFile = file("$targetDir/archon-viewer.html")

    inputs.file(sourceFile)
    outputs.file(targetFile)

    doLast {
        targetDir.mkdirs()
        sourceFile.copyTo(targetFile, overwrite = true)
        println("Copied canvas viewer: ${sourceFile.absolutePath} -> ${targetFile.absolutePath}")
    }
}

// Make sure copyCanvasViewer runs before archon-viz build
tasks.named("compileJava") {
    dependsOn("copyCanvasViewer")
}
