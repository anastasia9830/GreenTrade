plugins {
    id("java")
    id("application")
    id("io.freefair.lombok") version "8.6"
}

group = "de.tub"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

lombok {
    version.set("1.18.34")
}

dependencies {
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Flyway 10+ ТРЕБУЕТ модуль БД
    implementation("org.flywaydb:flyway-core:10.15.2")
    implementation("org.flywaydb:flyway-database-postgresql:10.15.2")

    // уберёт предупреждение SLF4J (не обязательно, но приятно)
    implementation("org.slf4j:slf4j-simple:2.0.13")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}


tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("de.tub.Main")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "de.tub.Main"
    }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
