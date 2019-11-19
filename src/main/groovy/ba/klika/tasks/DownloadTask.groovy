package ba.klika.tasks


import groovy.util.Proxy
import groovyx.net.http.RESTClient
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

import java.net.Proxy

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
     */
    @Input
    @Optional
    final Property<String> version = project.objects.property(String)

    @Input
    final Property<String> distributionGroup = project.objects.property(String)
    @Input
    final Property<String> outPath = project.objects.property(String)

    @TaskAction
    def download() {
        println "download task"

        if(releaseId.orNull){
            downloadWithReleaseId(releaseId.get())
        }
        downloadWithoutReleaseId()
    }

    def downloadWithReleaseId(String releaseId){
        println "downloadWithReleaseId"
        String url=getDownloadUrl("/apps/${ownerName.get()}/${appName.get()}/releases/${releaseId}")
        downloadFollowingRedirect(url, outPath.get())
    }

    def downloadWithoutReleaseId() {
        println "downloadWithoutReleaseId"

        if (!distributionGroup.get()) {
            throw new GradleException("distributionGroup field must not be empty!")
        }
        if (!ownerName.get()) {
            throw new GradleException("ownerName field must no tbe empty!")
        }
        if (!appName.get()) {
            throw new GradleException("appName field must no tbe empty!")
        }

        //use orNull because get throws exception
        if(buildNumber.orNull || version.orNull){
            println "buildNumber or version specified"
            RESTClient client = getRestClient("/apps/${ownerName.get()}/${appName.get()}/distribution_groups/${distributionGroup.get()}/releases")
            def callResponse = catchHttpExceptions { client.get(headers()) }
            String releaseId = null
            if (callResponse.status == 200) {
                def list = callResponse.getData()
                println list
                for (def entry in list){
                    println "Checking entry for version/buildNumber ${entry}"
                    if(buildNumber.orNull?.equals(entry.version) || version.orNull?.equals(entry.short_version)){
                        releaseId=entry.id
                        break
                    }
                }
                if(!releaseId){
                    throw new GradleException("No release found")
                }
                downloadWithReleaseId(releaseId)
            } else {
                throw new GradleException("Error calling AppCenter. Status code " + callResponse.status + '\n' + callResponse.getData().toString())
            }
        } else {
            String url=getDownloadUrl("/apps/${ownerName.get()}/${appName.get()}/distribution_groups/${distributionGroup.get()}/releases/latest")
            downloadFollowingRedirect(url, outPath.get())
        }
    }

    def getDownloadUrl(String endPoint){
        println "getDownloadUrl"

        RESTClient client = getRestClient(endPoint)
        def callResponse = catchHttpExceptions { client.get(headers()) }

        if (callResponse.status == 200) {
            return callResponse.getData().download_url
        } else {
            throw new GradleException("Error calling AppCenter. Status code " + callResponse.status + '\n' + callResponse.getData().toString())
        }
    }

    def downloadFollowingRedirect(String url, String filename) {
        new File(filename).getParentFile().mkdirs()
        project.logger.lifecycle("Downloading ${url}")
        def redirectLimit = 10

        while(url) {
            new URL(url).openConnection(proxy).with { conn ->
                conn.instanceFollowRedirects = false
                url = conn.getHeaderField( "Location" )
                if(!url) {
                    new File(filename).withOutputStream { out ->
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

