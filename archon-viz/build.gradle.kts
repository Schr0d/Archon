plugins {
    id("java")
}

dependencies {
    implementation(project(":archon-core"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.+")
    implementation("info.picocli:picocli:4.7.+")
    annotationProcessor("info.picocli:picocli-codegen:4.7.+")

    testImplementation(project(":archon-test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.+")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.archon.viz.ViewCommand"
    }
}

tasks.test {
    useJUnitPlatform()
}
