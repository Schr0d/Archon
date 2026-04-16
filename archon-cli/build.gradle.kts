plugins {
    id("com.github.johnrengelman.shadow") version "8.1.+"
}

// Read version from VERSION file
val archonVersion = file("../VERSION").takeIf { it.exists() }?.readText()?.trim() ?: "0.2.0"

dependencies {
    implementation(project(":archon-core"))
    runtimeOnly(project(":archon-java"))
    implementation(project(":archon-js"))
    implementation(project(":archon-python"))
    implementation("info.picocli:picocli:4.7.+")
    implementation("ch.qos.logback:logback-classic:1.5.+")
    annotationProcessor("info.picocli:picocli-codegen:4.7.+")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.+")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.archon.cli.ArchonCli"
    }
}

tasks.shadowJar {
    archiveBaseName.set("archon")
    archiveVersion.set(archonVersion)
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
