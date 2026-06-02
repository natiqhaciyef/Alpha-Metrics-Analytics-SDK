plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    compileOnly("com.android.tools.build:gradle:8.2.2")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")

    // 👈 CHANGE THIS LINE from compileOnly to implementation
    implementation("com.vanniktech:gradle-maven-publish-plugin:0.28.0")
}

gradlePlugin {
    plugins {
        register("modulePublication") {
            id = "module.publication"
            implementationClass = "ModulePublicationPlugin"
        }
    }
}