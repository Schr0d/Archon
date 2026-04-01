dependencies {
    implementation(project(":archon-core"))
    implementation(project(":archon-java"))
    implementation("info.picocli:picocli:4.7.+")
    implementation("ch.qos.logback:logback-classic:1.5.+")
    annotationProcessor("info.picocli:picocli-codegen:4.7.+")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.archon.cli.ArchonCli"
    }
}
