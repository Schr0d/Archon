dependencies {
    implementation(project(":archon-core"))
    implementation("com.github.javaparser:javaparser-core:3.28.+")
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.28.+")
    implementation("com.tngtech.archunit:archunit:1.3.+")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.+")
    testImplementation("org.springframework:spring-beans:6.1.+")
    testImplementation("org.springframework:spring-context:6.1.+")
    testImplementation("jakarta.annotation:jakarta.annotation-api:2.1.+")
}
