import org.jreleaser.model.Active

plugins {
  kotlin("jvm") version "1.9.23"
  kotlin("plugin.serialization") version "1.9.23"

  `java-library`
  `maven-publish`
  id("org.jreleaser") version "1.12.0"
}

val detektVersion: String by project

group = "ru.code4a"
version = file("version").readText().trim()

repositories {
  mavenCentral()
  mavenLocal()
}

java {
  withJavadocJar()
  withSourcesJar()
}

dependencies {
  implementation("net.mamoe.yamlkt:yamlkt:0.13.0")

  compileOnly("io.gitlab.arturbosch.detekt:detekt-api:$detektVersion")
  testImplementation("io.gitlab.arturbosch.detekt:detekt-test:$detektVersion")

  testImplementation("io.kotest:kotest-assertions-core:5.9.0")
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      artifactId = "usage-detection-detekt-plugin"

      from(components["java"])

      pom {
        name = "Usage Detection Detekt Plugin Extension"
        description =
          "The Usage Detection Detekt Plugin is a powerful tool for ensuring code quality and adherence to best practices by detecting and restricting the usage of functions within other functions based on configurable rules and annotations."
        url = "https://github.com/4ait/usage-detection-detekt-plugin"
        inceptionYear = "2024"
        licenses {
          license {
            name = "The Apache License, Version 2.0"
            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
          }
        }
        developers {
          developer {
            id = "tikara"
            name = "Evgeniy Simonenko"
            email = "tiikara93@gmail.com"
          }
        }
        scm {
          connection = "scm:git:git://github.com:4ait/usage-detection-detekt-plugin.git"
          developerConnection = "scm:git:ssh://github.com:4ait/usage-detection-detekt-plugin.git"
          url = "https://github.com/4ait/usage-detection-detekt-plugin"
        }
      }
    }
  }
  repositories {
    maven {
      url =
        layout.buildDirectory
          .dir("staging-deploy")
          .get()
          .asFile
          .toURI()
    }
  }
}

jreleaser {
  project {
    copyright.set("Company 4A")
  }
  gitRootSearch.set(true)
  signing {
    active.set(Active.ALWAYS)
    armored.set(true)
  }
  release {
    github {
      overwrite.set(true)
      branch.set("master")
    }
  }
  deploy {
    maven {
      mavenCentral {
        create("maven-central") {
          active.set(Active.ALWAYS)
          url.set("https://central.sonatype.com/api/v1/publisher")
          stagingRepositories.add("build/staging-deploy")
          retryDelay.set(30)
        }
      }
    }
  }
}

tasks.test {
  useJUnitPlatform()
}
