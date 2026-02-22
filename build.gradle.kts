plugins {
  kotlin("jvm") version "1.9.24"
  application
}

group = "com.identityforge.app"
version = "0.1.0"

repositories {
  mavenCentral()
}

dependencies {
  implementation("io.ktor:ktor-server-core-jvm:2.3.12")
  implementation("io.ktor:ktor-server-netty-jvm:2.3.12")
  implementation("io.ktor:ktor-server-html-builder-jvm:2.3.12")
  implementation("ch.qos.logback:logback-classic:1.5.6")

  implementation("org.xerial:sqlite-jdbc:3.46.1.3")

  testImplementation(kotlin("test"))
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

application {
  mainClass.set("com.identityforge.app.MainKt")
}

tasks.test {
  useJUnitPlatform()
}

kotlin {
  jvmToolchain(21)
}
