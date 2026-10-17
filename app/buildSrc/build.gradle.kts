// Build logic shared by the app's build scripts, with its own tests: ./gradlew -p buildSrc test
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
