import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
}

// Fix resolveMainClassName: separate tool MigrateH2FileToPostgres also has a main().
tasks.named<BootRun>("bootRun") {
    mainClass.set("com.aicrm.AiCrmApplicationKt")
}
tasks.named<BootJar>("bootJar") {
    mainClass.set("com.aicrm.AiCrmApplicationKt")
}

group = "com.aicrm"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.apache.pdfbox:pdfbox:3.0.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<JavaExec>("migrateH2ToPostgres") {
    group = "migration"
    description =
        "Copy CRM tables from H2 (H2_PATH) to Postgres (TARGET_JDBC_URL). Run scripts/migrate-h2-to-ai-crm-demo.sh to create DB + schema first."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.aicrm.tools.MigrateH2FileToPostgres")
}
