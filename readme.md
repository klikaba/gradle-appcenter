# AppCenter Gradle Plugin

## Summary

This plugin allows you to download Android and/or iOS apps from AppCenter. Common use 
case is mobile automation testing - you can use it to download fresh copy of the app 
before tests ware executed.

## How to install

File : `build.gradle`

```groovy
plugins {
  id "ba.klika.appcenter" version "1.2"
}
```

Or using legacy plugin application:

```groovy
buildscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath "gradle.plugin.ba.klika:appcenter:1.2"
  }
}

apply plugin: "ba.klika.appcenter"
```

## How to use

Generate AppCentar API token: `appcenter.ms` > `Account Settings` > `API Tokens`

File : `build.gradle`

```groovy
appcenter {
    apiToken = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
}
```

### Get Apps Task

To be able to download app you must know three things: Owner Name, App Name and name of Distribution group. 

There are multiple ways how to get it but easiest is just to run task `:getApps`:

```bash
➜ gradle :getApps             

> Task :getApps
--------------------------------------------------------------------------------------------------------------------
                                  id|             os|           ownerName|       appName| groups
--------------------------------------------------------------------------------------------------------------------
901ed488-d57b-4b2c-a425-cc66d36d5cdb|            iOS|               Klika|       TestApp| Collaborators,QA
b5f2a3db-b6da-430e-916e-39b851a42bce|        Android|               Klika|     TestApp-1| Collaborators,QA
f1e96430-d476-4f22-ac85-8f8fd282a582|        Android|               Klika|  MyWallet-AOS| Android tester,Collaborators
2d12e94a-853a-44e6-9e5e-aae97ab86539|            iOS|               Klika|  MyWallet-iOS| Collaborators,IOS testers
---------------------------------------------------------------------------------------------------------------------

BUILD SUCCESSFUL in 9s
1 actionable task: 1 executed
```

In case that you want to run `:download` task before tests are executed just add:

File : `build.gradle`

```groovy
appcenter {
    apiToken = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
}

test.dependsOn {
    download {
        ownerName = 'Klika'
        appName = 'TestApp'
        distributionGroup = 'QA'
        outPath = './test-app.ipa'
    }
}
```

This will download latest release of `TestApp`.

### Multiple Downloads

Usually you will want to download Android and iOS app and execute same tests on them. To execute more than one download
task you can do something like:

```groovy
task downloadIPA(type: ba.klika.tasks.DownloadTask) {
    apiToken = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
    ownerName = 'Klika'
    appName = 'TestApp'
    distributionGroup = 'QA'
    outPath = './test-app.ipa'
}

task downloadAPK(type: ba.klika.tasks.DownloadTask) {
    apiToken = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
    ownerName = 'Klika'
    appName = 'TEstApp-1'
    distributionGroup = 'QA'
    outPath = './test.apk'
}

test.dependsOn {
    downloadIPA
}

test.dependsOn {
    downloadAPK
}
```


### Example 1

Simple script to download APK from AppCenter before tests:

```groovy
plugins {
    id 'java'
    id "ba.klika.appcenter" version "1.2"
}

group 'ba.klika.appcenter.automation-test'
version '1.0-SNAPSHOT'

repositories {
    mavenCentral()
}

dependencies {
    testCompile group: 'junit', name: 'junit', version: '4.12'
}

appcenter {
    apiToken = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
}

test.dependsOn {
    download {
        ownerName = 'Klika'
        appName = 'TestApp'
        distributionGroup = 'QA'
        outPath = './test-app.ipa'
    }
}
```

### Example 2

Simple script to download APK and IPA build from AppCenter before tests:

```groovy
plugins {
    id 'java'
    id "ba.klika.appcenter" version "1.2"
}

group 'ba.klika.appcenter.automation-test'
version '1.0-SNAPSHOT'

repositories {
    mavenCentral()
}

dependencies {
    testCompile group: 'junit', name: 'junit', version: '4.12'
}

appcenter {
    apiToken = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
}

task downloadIPA(type: ba.klika.tasks.DownloadTask) {
    apiToken = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
    ownerName = 'Klika'
    appName = 'TestApp'
    distributionGroup = 'QA'
    outPath = './test-app.ipa'
}

task downloadAPK(type: ba.klika.tasks.DownloadTask) {
    apiToken = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
    ownerName = 'Klika'
    appName = 'TestApp-1'
    distributionGroup = 'QA'
    outPath = './test-app.apk'
}

test.dependsOn {
    downloadIPA
}

test.dependsOn {
    downloadAPK
}
```