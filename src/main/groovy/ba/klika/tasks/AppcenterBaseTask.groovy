package ba.klika.tasks

import groovyx.net.http.ContentType
import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.HttpResponseException

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import groovyx.net.http.RESTClient

abstract class AppcenterBaseTask extends DefaultTask {
    @Input
    final Property<String> apiToken = project.objects.property(String)

    private final String proxyHost
    private final Integer proxyPort
    protected final Proxy proxy

    AppcenterBaseTask() {
        proxyHost = System.properties.get("http.proxyHost")
        proxyPort = System.properties.get("http.proxyPort") as Integer
        if(proxyHost && proxyPort){
            proxy=new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort))
        } else {
            proxy=Proxy.NO_PROXY
        }
    }

    protected RESTClient getRestClient(String path) {
        def url = 'https://api.appcenter.ms/v0.1' + path
        RESTClient restClient = new RESTClient(url)

        if(proxyHost && proxyPort){
            restClient.setProxy(proxyHost,proxyPort,"http")
        }
        return restClient
    }

    protected HttpResponseDecorator catchHttpExceptions(Closure callClosure) {
        try {
            return callClosure.call()
        } catch (HttpResponseException responseException) {
            return responseException.response
        }
    }

    protected Map<String, String> headers() {
        return ['headers' : ['X-API-Token' : apiToken.get()],
                requestContentType: ContentType.JSON
        ]
    }
}