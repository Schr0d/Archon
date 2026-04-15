plugins {
    id("java-library")
}

dependencies {
    // JSON parsing for dependency-cruiser output
    implementation("com.google.code.gson:gson:2.10.1")

    // Core module
    implementation(project(":archon-core"))

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.mockito:mockito-core:5.3.1")
}

tasks.test {
    useJUnitPlatform()
}
