import org.gradle.api.Plugin
import org.gradle.api.Project
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost

class ModulePublicationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // 1. Automatically apply the required publishing plugins to the target module
            pluginManager.apply("com.vanniktech.maven.publish")
            pluginManager.apply("maven-publish")

            // 2. Configure the publishing extensions exactly like the screenshot
            val mavenPublishing = extensions.getByType(MavenPublishBaseExtension::class.java)

            mavenPublishing.apply {
                // Define your coordinates
                coordinates(
                    groupId = "io.github.natiqhaciyef",
                    artifactId = "alpha-metrics-analytics",
                    version = "1.0.0"
                )

                // Configure POM Metadata
                pom {
                    name.set("Alpha Metrics Analytics SDK")
                    description.set("A high-performance, native-backed analytics tracking engine for Android applications.")
                    inceptionYear.set("2026")
                    url.set("https://github.com/natiqhaciyef/Alpha-Metrics-Analytics-SDK")

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            id.set("natiqhaciyef")
                            name.set("Natig Hajiyev")
                            email.set("natiq00h2272@gmail.com")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/natiqhaciyef/Alpha-Metrics-Analytics-SDK.git")
                        developerConnection.set("scm:git:ssh://github.com:natiqhaciyef/Alpha-Metrics-Analytics-SDK.git")
                        url.set("https://github.com/natiqhaciyef/Alpha-Metrics-Analytics-SDK")
                    }
                }

                // Automatically target the new Sonatype Central portal host endpoint
                publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

                // Enables automatic signing if GPG keys are configured in your environment properties
                signAllPublications()
            }
        }
    }
}