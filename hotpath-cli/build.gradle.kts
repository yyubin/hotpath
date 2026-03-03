plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(project(":hotpath-core"))
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")
}

tasks.shadowJar {
    archiveBaseName = "hotpath"
    archiveClassifier = ""
    archiveVersion = ""
    manifest {
        attributes["Main-Class"] = "org.yyubin.hotpath.Main"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
