plugins {
    id("java-library")
}

dependencies {
    // Closure Compiler for JS/TS parsing
    implementation("com.google.javascript:closure-compiler:v20240317") {
        exclude(group = "com.google.code.gson", module = "gson")
    }

    // Core module
    implementation(project(":archon-core"))

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.mockito:mockito-core:5.3.1")
}

tasks.test {
    useJUnitPlatform()
}
