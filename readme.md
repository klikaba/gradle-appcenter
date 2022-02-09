# AppCenter Gradle Plugin

## Summary

This plugin allows you to download Android and/or iOS apps from AppCenter. Common use 
case is mobile automation testing - you can use it to download fresh copy of the app 
before tests ware executed.

## How to install

File : `build.gradle`

```groovy
plugins {
  id "ba.klika.appcenter" version "1.7"
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
    classpath "gradle.plugin.ba.klika:appcenter:1.7"
  }
}

apply plugin: "ba.klika.appcenter"
```

## How to use

Generate AppCentar API token: `appcenter.ms` > `Account Settings` > `API Tokens`

File : `build.gradle`

```groovy
appcenter {
    apiToken = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
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
### Download Task
#### Arguments

* Required
  - `ownerName`
  - `appName`
  - `distributionGroup`
  - `outPath`: Directory Path where the app will be saved
* Optional
  - `releaseId`: id in [App Center API](https://openapi.appcenter.ms/#/distribute/releases_listByDistributionGroup) (not id of getApps task)
  - `releaseVersion`: short_version in [App Center API](https://openapi.appcenter.ms/#/distribute/releases_listByDistributionGroup)
  - `skipDownload`: when setting this flag to true the actual download is skipped (in case you want to call the download URL yourself)
  
You can use any combination of above optional arguments. \
The order of importance for resolving to a specific release is as follows: 

`releaseId > > releaseVersion > nothing (=latest)`
 
Before the actual download starts, it check for a file with matching naming of `$appName_$shortVersion_$version.$fileExtension` in the output folder and the `fingerprint` (md5) received from app center api. If both matches download will be skipped.
  
In case that you want to run `:download` task before tests are executed just add:

File : `build.gradle`

```groovy
appcenter {
    apiToken = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
}

test.dependsOn {
    download {
        ownerName = "Klika"
        appName = "TestApp"
        distributionGroup = "QA"
        outPath = "$project.projectDir"
    }
}
```

This will download latest release of `TestApp`.

#### Multiple Downloads

Usually you will want to download Android and iOS app and execute same tests on them. To execute more than one download
task you can do something like:

```groovy
task downloadIPA(type: ba.klika.tasks.DownloadTask) {
    apiToken = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    ownerName = "Klika"
    appName = "TestApp"
    distributionGroup = "QA"
    outPath = "$project.projectDir/app"
}

task downloadAPK(type: ba.klika.tasks.DownloadTask) {
    apiToken = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    ownerName = "Klika"
    appName = "TestApp-1"
    distributionGroup = "QA"
    outPath = "$project.projectDir/app"
}

test.dependsOn {
    downloadIPA
}

test.dependsOn {
    downloadAPK
}
```

#### Accessing version information of downloaded app
After running the download task you can access all the version information with the argument names also used to define it, for example to pass them into some testing task. \
They get populated with the information fetched from app center api during the download task and therefore are also available in case you did not specify them before.  
```groovy
task printVersionInfo(){
    dependsOn(downloadAPK)
    doLast {
        println("appName:"+tasks.downloadAPK.appName.getOrNull())
        println("releaseId:"+tasks.downloadAPK.releaseId.getOrNull())
        println("releaseVersion:"+tasks.downloadAPK.releaseVersion.getOrNull())
    }
}
```

#### Cleanup target folder
As the plugin stores app version for reuse and does not cleanup itself you need to take care of it using a gradle task like the following:
```groovy
task deleteAppFiles(type: Delete) {
    dependsOn(clean)
    def cutoff = LocalDateTime.now().minusWeeks(1) //remove all files which got modified before today minus 1 week
    delete fileTree (dir: "$project.projectDir/app/")
            .matching{ include '*.apk', '*.ipa' }
            .findAll {
                def fileDate = Instant.ofEpochMilli(it.lastModified()).atZone(ZoneId.systemDefault()).toLocalDateTime()
                fileDate.isBefore(cutoff)
            }
}
```

### Example 1

Simple script to download APK from AppCenter before tests:

```groovy
plugins {
    id 'java'
    id 'ba.klika.appcenter' version '1.7'
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
        outPath = './'
    }
}
```

### Example 2

Simple script to download APK and IPA build from AppCenter before tests:

```groovy
plugins {
    id 'java'
    id 'ba.klika.appcenter' version '1.7'
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
    outPath = './'
}

task downloadAPK(type: ba.klika.tasks.DownloadTask) {
    apiToken = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
    ownerName = 'Klika'
    appName = 'TestApp-1'
    distributionGroup = 'QA'
    outPath = './'
}

test.dependsOn {
    downloadIPA
}

test.dependsOn {
    downloadAPK
}
```

### Example with optional arguments, cleanup task and nebula override
```groovy
import ba.klika.tasks.DownloadTask

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

plugins {
    id 'java'
    id "ba.klika.appcenter" version "1.7"
    id 'nebula.override' version '3.0.2'
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

task downloadIPA(type: DownloadTask) {
    apiToken = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
    ownerName = 'Klika'
    appName = 'TestApp-1'
    distributionGroup = 'QA'
    outPath = "$project.projectDir/app/"
}

/*
 * to call from command line and override settings use:
 *
 * gradle -Doverride.downloadAPK.releaseId="989" downloadAPK
 * (made possible by nebula.override plugin)
 *
 * Prioritization:
 * releaseId > releaseVersion > nothing (=latest)
 */
task downloadAPK(type: DownloadTask) {
    apiToken = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
    ownerName = 'Klika'
    appName = 'TestApp'
    distributionGroup = 'QA'
    releaseVersion = "1.2.3"
    //releaseId="10"
    outPath = "$project.projectDir/app/"
}

task printVersionInfo(){
    dependsOn(downloadAPK)
    doLast {
        println("appName:"+tasks.downloadAPK.appName.getOrNull())
        println("releaseId:"+tasks.downloadAPK.releaseId.getOrNull())
        println("releaseVersion:"+tasks.downloadAPK.releaseVersion.getOrNull())
    }
}

task deleteAppFiles(type: Delete) {
    dependsOn(clean)
    def cutoff = LocalDateTime.now().minusWeeks(1)
    delete fileTree (dir: "$project.projectDir/app/")
            .matching{ include '*.apk', '*.ipa' }
            .findAll {
                def fileDate = Instant.ofEpochMilli(it.lastModified()).atZone(ZoneId.systemDefault()).toLocalDateTime()
                fileDate.isBefore(cutoff)
            }
}

test.dependsOn {
    downloadIPA
}

test.dependsOn {
    downloadAPK
}
```

### Example DownloadTask usage with skipDownload flag

```
task downloadAPK(type: DownloadTask) {
    group = 'verification'
    apiToken = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
    ownerName = 'BAWAG-P-S-K-Organization'
    appName = 'Klar-POC-1'
    distributionGroup = 'BAWAG-PSK-LUCY'
    //releaseVersion = "2.6.0"
    //releaseId="982"
    skipDownload = true
    outPath = "$project.projectDir/app/"

    doLast {
        println(downloadURL)  // if you don't access downloadUrl in doLast it won't be set yet
    }
}
```

## Proxy Support
The plugin picks up the same variables which are used to set the proxy for gradle in `gradle.properties` \
To be more specific:
 - `http.proxyHost`
 - `http.proxyPort`
 
**Authentication and http.nonProxyHosts is not supported.**
