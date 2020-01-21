// google apis
val googleApiClientVersion = "1.30.2"
val googleDirectoryServiceVersion = "directory_v1-rev110-1.25.0"
val googleOAuthHttpVersion = "0.17.0"

// common libs
val commonsEmailVersion = "1.5"
val jacksonVersion = "2.9.9"
val jacksonDatabindVersion = "2.9.9.3"
val janinoVersion = "3.1.0"
val javaMailVersion = "1.6.4"
val logbackVersion = "1.2.3"
val slf4jVersion = "1.7.26"
val snakeYamlVersion = "1.25"
val unboundidLdapSdkVersion = "4.0.11"

plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "5.2.0"
}

tasks.wrapper {
    gradleVersion = "6.0.1"
    distributionType = Wrapper.DistributionType.ALL
}

repositories {
    mavenCentral()
}

dependencies {
    // logging
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.slf4j:log4j-over-slf4j:$slf4jVersion")
    implementation("org.slf4j:jcl-over-slf4j:$slf4jVersion")
    implementation("org.slf4j:jul-to-slf4j:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.codehaus.janino:janino:$janinoVersion")

    // app specific
    implementation("com.unboundid:unboundid-ldapsdk:$unboundidLdapSdkVersion")
    implementation("org.yaml:snakeyaml:$snakeYamlVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonDatabindVersion")
    implementation("com.sun.mail:mailapi:$javaMailVersion")
    implementation("com.sun.mail:smtp:$javaMailVersion")
    implementation("org.apache.commons:commons-email:$commonsEmailVersion")

    // google
    implementation("com.google.api-client:google-api-client:$googleApiClientVersion")
    implementation("com.google.apis:google-api-services-admin-directory:$googleDirectoryServiceVersion")
    implementation("com.google.auth:google-auth-library-oauth2-http:$googleOAuthHttpVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

application {
    mainClassName = "com.kvaster.gsuite.App"
}

configurations.forEach {
    it.exclude("org.apache.httpcomponents", "httpclient")
    it.exclude("org.apache.httpcomponents", "httpcore")

    it.exclude("com.sun.mail", "javax.mail")
    it.exclude("javax.activation", "activation")
}
