plugins {
    id("java")
}

dependencies {
    implementation(project(":archon-core"))
    implementation(project(":archon-viz-web"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.+")
    implementation("info.picocli:picocli:4.7.+")
    annotationProcessor("info.picocli:picocli-codegen:4.7.+")

    testImplementation(project(":archon-test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.+")
}

// Ensure canvas viewer is copied before processing resources
tasks.processResources {
    dependsOn(project(":archon-viz-web").tasks.named("copyCanvasViewer"))
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.archon.viz.ViewCommand"
    }
}

tasks.test {
    useJUnitPlatform()
}
