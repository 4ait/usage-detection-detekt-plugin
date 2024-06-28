plugins {
  `java-library`
  `maven-publish`
  signing
  id("io.github.gradle-nexus.publish-plugin") version "2.0.0"

  kotlin("jvm") version "1.9.23"
  kotlin("plugin.serialization") version "1.9.23"
}

val detektVersion: String by project

group = "ru.code4a"
version = "0.1.0"

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

nexusPublishing {
  repositories {
    sonatype {
      nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
      snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
    }
  }
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
        properties = mapOf()
        licenses {
          license {
            name = "The Apache License, Version 2.0"
            url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
          }
        }
        developers {
        }
        scm {
          connection = "scm:git:git://example.com/my-library.git"
          developerConnection = "scm:git:ssh://example.com/my-library.git"
          url = "http://example.com/my-library/"
        }
      }
    }
  }
}

signing {
  sign(publishing.publications["mavenJava"])
}

tasks.test {
  useJUnitPlatform()
}
