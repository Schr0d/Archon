dependencies {
    implementation(project(":archon-core"))
    implementation("com.github.javaparser:javaparser-core:3.28.+")
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.28.+")
    implementation("com.tngtech.archunit:archunit:1.3.+")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.+")
}
