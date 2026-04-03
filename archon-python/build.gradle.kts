plugins {
    id("java-library")
}

dependencies {
    // Core module (LanguagePlugin SPI)
    implementation(project(":archon-core"))

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.mockito:mockito-core:5.3.1")
}

tasks.test {
    useJUnitPlatform()
}
