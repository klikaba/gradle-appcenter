package ba.klika.tasks

import groovyx.net.http.RESTClient
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class DownloadTask extends AppcenterBaseTask {
    @Input
    final Property<String> ownerName = project.objects.property(String)
    @Input
    final Property<String> appName = project.objects.property(String)
    @Input
    final Property<String> distributionGroup = project.objects.property(String)
    @Input
    final Property<String> outPath = project.objects.property(String)

    @TaskAction
    def download() {
        downloadWithoutReleaseId()
    }

    def downloadWithoutReleaseId() {

        if (!distributionGroup.get()) {
            throw new GradleException("distributionGroup field must no tbe empty!")
        }
        if (!ownerName.get()) {
            throw new GradleException("ownerName field must no tbe empty!")
        }
        if (!appName.get()) {
            throw new GradleException("appName field must no tbe empty!")
        }

        RESTClient client = getRestClient("/apps/${ownerName.get()}/${appName.get()}/distribution_groups/${distributionGroup.get()}/releases/latest")
        def callResponse = catchHttpExceptions { client.get(headers()) }

        if (callResponse.status == 200) {
            def url = callResponse.getData().download_url
            project.logger.lifecycle("Downloading ${url}")
            downloadFollowingRedirect(url, outPath.get())
        } else {
            throw new GradleException("Error calling AppCenter. Status code " + callResponse.status + '\n' + callResponse.getData().toString())
        }
    }

    def downloadFollowingRedirect(String url, String filename) {
        def redirectLimit = 10
        while(url) {
            new URL(url).openConnection().with { conn ->
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

