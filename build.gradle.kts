plugins {
    kotlin("jvm") version "2.3.20"
}
val minecraftVersion = "1.21.8"

group = "io.atlantica"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://mvn.devos.one/releases")
}

dependencies {
    testImplementation(kotlin("test"))

    //Networking
    implementation("io.ktor:ktor-server-netty:3.5.0")

    //Config
    implementation("org.tomlj:tomlj:1.1.1")

    //logging
    implementation("org.slf4j:slf4j-nop:2.0.9")
    api("cz.lukynka:pretty-log:2.0")
}

tasks.test {
    useJUnitPlatform()
}