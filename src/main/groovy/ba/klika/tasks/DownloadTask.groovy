package ba.klika.tasks

import groovyx.net.http.RESTClient
import org.apache.commons.codec.digest.DigestUtils
import org.apache.http.HttpStatus
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.Internal
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
    @Input
    final Property<String> distributionGroup = project.objects.property(String)
    @Input
    final Property<String> outPath = project.objects.property(String)
    @Input @Optional
    final Property<Boolean> skipDownload = project.objects.property(Boolean)
    @Input @Optional
    final Property<String> releaseId = project.objects.property(String)
    @Input @Optional
    final Property<String> releaseVersion = project.objects.property(String)

    @TaskAction
    def download() {        
        def data = getBuildData();
        if(skipDownload.getOrElse(false)){
            logger.lifecycle("Skipping download as 'skipDownload' is true");
        } else {
            downloadOrUseExistingFile(data);
        }
    }

    @Internal
    def getBuildData() {
        def buildData;
        if(releaseId.present){
            logger.lifecycle("releaseId ${releaseId.get()} specified");
            buildData = getReleaseData(releaseId.get());
        } else {
            if(releaseVersion.present){
                project.logger.lifecycle("releaseVersion ${releaseVersion.get()} specified");
                
                RESTClient client = getRestClient("/apps/${ownerName.get()}/${appName.get()}/distribution_groups/${distributionGroup.get()}/releases");
                def callResponse = catchHttpExceptions { client.get(headers()) }

                if (callResponse.status == HttpStatus.SC_OK) {
                    def list = callResponse.getData();
                    def release = list.find{it.short_version == releaseVersion.get()};
                    if(!release){
                        throw new GradleException("No release found for releaseVersion ${releaseVersion.get()}");
                    }
                    buildData = getReleaseData(release.id as String)
                } else {
                    throw new GradleException("Error calling AppCenter. Status code " + callResponse.status + '\n' + callResponse.getData().toString())
                }
            } else {
                logger.lifecycle("No releaseId / releaseVersion specified, getting data of latest build");
                buildData = getData("/apps/${ownerName.get()}/${appName.get()}/distribution_groups/${distributionGroup.get()}/releases/latest");
            }
        }
        return buildData;
    }

    def getReleaseData(String releaseIdString){
        project.logger.lifecycle("Getting release data with releaseId ${releaseIdString}");
        return getData("/apps/${ownerName.get()}/${appName.get()}/releases/${releaseIdString}");
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

    def downloadOrUseExistingFile(def data) {
        String shortVersion = data.short_version
        String version = data.version
        String md5 = data.fingerprint
        String url = data.download_url
        String fileExtension = data.fileExtension
    
        Path targetPath= Paths.get(outPath.getOrElse('./'));
        String versionFileName = appName.get() + "_" + shortVersion + "_" + version + "." + fileExtension;
        Path versionPath = targetPath.resolve(versionFileName);

        if(Files.notExists(versionPath) || !generateMD5(versionPath).equalsIgnoreCase(md5)) {
            download(url, versionPath);
        } else {
            logger.lifecycle("Skipping Download");
            logger.lifecycle("App with given build information exists in : $versionPath");
        }
    }

    static String generateMD5(Path path) {
        path.withInputStream {
            DigestUtils.md5Hex(it)
        }
    }

    def download(String url, Path targetPath) {
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
                throw new GradleException("Url have more than 10 redirects.");
            }
        }
    }
}

