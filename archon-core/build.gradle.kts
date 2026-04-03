dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.+")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.+")
    implementation("org.slf4j:slf4j-api:2.0.+")
    testImplementation(project(":archon-python"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.+")
}
