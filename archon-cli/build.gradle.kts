plugins {
    id("com.github.johnrengelman.shadow") version "8.1.+"
}

dependencies {
    implementation(project(":archon-core"))
    implementation(project(":archon-java"))
    implementation(project(":archon-js"))
    implementation(project(":archon-python"))
    implementation("info.picocli:picocli:4.7.+")
    implementation("ch.qos.logback:logback-classic:1.5.+")
    annotationProcessor("info.picocli:picocli-codegen:4.7.+")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.archon.cli.ArchonCli"
    }
}

tasks.shadowJar {
    archiveBaseName.set("archon")
    archiveVersion.set("0.2.0")
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
