package ba.klika.tasks


import groovyx.net.http.RESTClient
import org.apache.http.HttpStatus
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Instant

class DownloadTask extends AppcenterBaseTask {
    /**
     * Provider represents a value that can only be queried and cannot be changed.
     * Property represents a value that can be queried and also changed.
     * https://docs.gradle.org/current/userguide/lazy_configuration.html
     */
    @Input
    final Property<String> ownerName = project.objects.property(String)
    @Input
    final Property<String> appName = project.objects.property(String)
    /**
     * This is NOT the appcenters build ID.
     * At some appcenter endpoints it is named "buildNumber", on some other its "version" (whereas "short_version" is something like "2.6.0")
     * From Appcenter Swagger: The release's version.<br>\nFor iOS: CFBundleVersion from info.plist.<br>\nFor Android: android:versionCode from AppManifest.xml.\n
     */
    @Input
    @Optional
    final Property<String> buildNumber = project.objects.property(String)

    /**
     * if releaseId is specified use it directly
     */
    @Input
    @Optional
    final Property<String> releaseId = project.objects.property(String)

    /**
     * short_version like "2.6.0"
     * From appcenter swagger: The release's short version.<br>\nFor iOS: CFBundleShortVersionString from info.plist.<br>\nFor Android: android:versionName from AppManifest.xml.\n",
     *
     * if only version is specified get latest matching build (don't rely on order of data)
     * if also build number is given match on both
     */
    @Input
    @Optional
    final Property<String> releaseVersion = project.objects.property(String)

    @Input
    final Property<String> distributionGroup = project.objects.property(String)

    @Input
    final Property<String> outPath = project.objects.property(String)

    @TaskAction
    def download() {
        if(releaseId.present){
            downloadWithReleaseId(releaseId.get())
        }
        downloadWithoutReleaseId()
    }

    def downloadWithReleaseId(String releaseId){
        println "downloadWithReleaseId ${releaseId}"
        def data=getData("/apps/${ownerName.get()}/${appName.get()}/releases/${releaseId}")
        downloadFollowingRedirect(data.download_url, outPath.get(), data.short_version, data.version)
    }

    def downloadWithoutReleaseId() {
        println "downloadWithoutReleaseId"

        /*  this is redundant as @Input already checks for presence
        if (!distributionGroup.get()) {
            throw new GradleException("distributionGroup field must not be empty!")
        }
        if (!ownerName.get()) {
            throw new GradleException("ownerName field must no tbe empty!")
        }
        if (!appName.get()) {
            throw new GradleException("appName field must no tbe empty!")
        }*/

        if(buildNumber.present || releaseVersion.present){
            println "buildNumber or version specified"

            RESTClient client = getRestClient("/apps/${ownerName.get()}/${appName.get()}/distribution_groups/${distributionGroup.get()}/releases")
            def callResponse = catchHttpExceptions { client.get(headers()) }

            if (callResponse.status == HttpStatus.SC_OK) {
                def list = callResponse.getData()
                def release = getRelease(list, buildNumber.orNull, releaseVersion.orNull)

                if(!release){
                    throw new GradleException("No release found")
                }
                println (release)
                downloadWithReleaseId(release.id as String)
            } else {
                throw new GradleException("Error calling AppCenter. Status code " + callResponse.status + '\n' + callResponse.getData().toString())
            }
        } else {
            def data=getData("/apps/${ownerName.get()}/${appName.get()}/distribution_groups/${distributionGroup.get()}/releases/latest")
            downloadFollowingRedirect(data.download_url, outPath.get(), data.short_version, data.version)
        }
    }

    static def getRelease(releaseList, String buildNumberString, String releaseVersionString) {
        def release
        if (buildNumberString) {
            release = releaseList.find {
                                    it.version == buildNumberString &&
                                    (releaseVersionString==null || releaseVersionString == it.short_version) //check also for releaseVersion if present
                                  }
        } else {
            //search for latest release of releaseVersion
            release = releaseList.findAll { it.short_version == releaseVersionString }
                    .max { Instant.parse(it.uploaded_at) }
        }
        return release
    }

    def getData(String endPoint){
        RESTClient client = getRestClient(endPoint)
        def callResponse = catchHttpExceptions { client.get(headers()) }

        if (callResponse.status == 200) {
            return callResponse.getData()
        } else {
            throw new GradleException("Error calling AppCenter. Status code " + callResponse.status + '\n' + callResponse.getData().toString())
        }
    }

    def downloadFollowingRedirect(String url, String filename, String releaseVersionString, String buildNumberString) {
        Path targetPath=Paths.get(filename)
        println("Target path: "+targetPath.toAbsolutePath())
        Path targetDir=targetPath.getParent()

        Files.createDirectories(targetDir) //new File(filename).getParentFile().mkdirs()
        String versionFileName=appName.get()+"_"+releaseVersionString+"_"+buildNumberString+".apk"
        Path versionPath=targetDir.resolve(versionFileName)

        //TODO add forceDownload flag
        if(Files.notExists(versionPath)) {
            project.logger.lifecycle("Downloading ${url}")
            def redirectLimit = 10

            while (url) {
                new URL(url).openConnection(proxy).with { conn ->
                    conn.instanceFollowRedirects = false
                    url = conn.getHeaderField("Location")
                    if (!url) {
                        versionPath.withOutputStream { out ->
                            conn.inputStream.with { inp ->
                                out << inp
                                inp.close()
                            }
                        }
                    }
                }
                redirectLimit--
                if (redirectLimit == 0) {
                    throw new GradleException("Url have more than 10 redirects.")
                }
            }
        } else {
            println("Using existing app file")
        }
        Files.copy(versionPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }
}

