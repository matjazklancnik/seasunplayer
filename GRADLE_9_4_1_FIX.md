# Gradle wrapper fix

Android Gradle Plugin 9.2.1 in this project uses Gradle 9.4.1.
This project sets:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.4.1-bin.zip
```

File:

```text
gradle/wrapper/gradle-wrapper.properties
```

The project requires JDK 17 and Android SDK 36. Verify the setup with:

```bash
./gradlew :app:compileDebugKotlin
```
