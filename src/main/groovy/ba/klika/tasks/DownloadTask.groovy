package ba.klika.tasks


import groovyx.net.http.RESTClient
import org.apache.commons.codec.digest.DigestUtils
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
    Property<String> buildNumber = project.objects.property(String)

    /**
     * if releaseId is specified use it directly
     */
    @Input
    @Optional
    Property<String> releaseId = project.objects.property(String)

    /**
     * short_version like "2.6.0"
     * From appcenter swagger: The release's short version.<br>\nFor iOS: CFBundleShortVersionString from info.plist.<br>\nFor Android: android:versionName from AppManifest.xml.\n",
     *
     * if only version is specified get latest matching build (don't rely on order of data)
     * if also build number is given match on both
     */
    @Input
    @Optional
    Property<String> releaseVersion = project.objects.property(String)

    @Input
    final Property<String> distributionGroup = project.objects.property(String)

    @Input
    final Property<String> outPath = project.objects.property(String)

    @Input
    @Optional
    final Property<Boolean> skipDownload = project.objects.property(Boolean)

    @TaskAction
    def download() {

        def releaseData = getReleaseData();
        setGradleVariables(releaseData);

        if(skipDownload.getOrElse(false)){
            logger.lifecycle("skipping download");
        } else {
            downloadOrUseExistingFile(releaseData);
        }
    }

    def getReleaseData() {
        def releaseData;
        if(releaseId.present){
            releaseData = getReleaseData(releaseId.get());
        } else {
            logger.lifecycle("get release data without releaseId")

            if(buildNumber.present || releaseVersion.present){
                logger.lifecycle("buildNumber or version specified")

                RESTClient client = getRestClient("/apps/${ownerName.get()}/${appName.get()}/distribution_groups/${distributionGroup.get()}/releases")
                def callResponse = catchHttpExceptions { client.get(headers()) }

                if (callResponse.status == HttpStatus.SC_OK) {
                    def list = callResponse.getData()
                    def release = getRelease(list, buildNumber.orNull, releaseVersion.orNull)

                    if(!release){
                        throw new GradleException("No release found")
                    }
                    releaseData = getReleaseData(release.id as String)
                } else {
                    throw new GradleException("Error calling AppCenter. Status code " + callResponse.status + '\n' + callResponse.getData().toString())
                }
            } else {
                logger.lifecycle("no version information specified, getting data of latest release")
                releaseData = getData("/apps/${ownerName.get()}/${appName.get()}/distribution_groups/${distributionGroup.get()}/releases/latest")
            }
        }
        return releaseData;
    }

    def getReleaseData(String releaseIdString){
        logger.lifecycle("get release data with releaseId ${releaseIdString}")
        return getData("/apps/${ownerName.get()}/${appName.get()}/releases/${releaseIdString}")
    }

    def setGradleVariables(data){
        //set version information so it can be used in gradle buildscript after executin the task
        buildNumber=project.objects.property(String) //just setting did not work
        buildNumber.set(data.version)
        releaseVersion=project.objects.property(String)
        releaseVersion.set(data.short_version)
        releaseId=project.objects.property(String)
        releaseId.set(data.id as String)
        //TODO set data.download_url to some output property
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

    private static String getFileExtension(Path path) {
        String name = path.getFileName().toString()
        int lastIndexOf = name.lastIndexOf(".")
        if (lastIndexOf == -1) {
            return "" // empty extension
        }
        return name.substring(lastIndexOf)
    }

    static String generateMD5(Path path) {
        path.withInputStream {
            DigestUtils.md5Hex(it) //using apache common utils
            //or plain groovy / Java
            /*new DigestInputStream(it, MessageDigest.getInstance('MD5')).withStream {
                //DigestInputStream calculates the digest of the files sent through the stream.
                //Therefore we need to consume the stream with the following line
                it.eachByte 4096, {buffer, length -> }
                it.messageDigest.digest().encodeHex() as String
            }*/
        }
    }

    def downloadOrUseExistingFile(def data) {

        String releaseVersionString=data.short_version
        String buildNumberString=data.version
        String md5=data.fingerprint
        String url=data.download_url

        Path targetPath=Paths.get(outPath.get())
        logger.lifecycle("Target path: $targetPath")
        Path targetDir=targetPath.getParent()

        Files.createDirectories(targetDir)
        String versionFileName=appName.get()+"_"+releaseVersionString+"_"+buildNumberString+getFileExtension(targetPath)
        Path versionPath=targetDir.resolve(versionFileName)

        if(Files.notExists(versionPath) || !generateMD5(versionPath).equalsIgnoreCase(md5)) {
            download(url, versionPath)
        } else {
            logger.lifecycle("Using existing app file: $versionPath")
        }
        Files.copy(versionPath, targetPath, StandardCopyOption.REPLACE_EXISTING)
    }

    def download(String url, Path targetPath) {
        project.logger.lifecycle("Downloading ${url}")
        def redirectLimit = 10

        while (url) {
            new URL(url).openConnection(proxy).with { conn ->
                conn.instanceFollowRedirects = false
                url = conn.getHeaderField("Location")
                if (!url) {
                    targetPath.withOutputStream { out ->
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
    }
}

