package features.filters.clientauthn.connectionpooling

import framework.ReposeConfigurationProvider
import framework.ReposeValveLauncher
import framework.TestProperties
import framework.mocks.MockIdentityService
import org.rackspace.deproxy.Deproxy
import org.rackspace.deproxy.Endpoint
import org.rackspace.deproxy.Handling
import spock.lang.Specification

/**
 * Created with IntelliJ IDEA.
 * User: izrik
 *
 */
class AuthNConnectionPoolingTest extends Specification {

    int reposePort
    int reposeStopPort
    int originServicePort
    int identityServicePort
    String urlBase

    MockIdentityService identityService

    Deproxy deproxy
    Endpoint originEndpoint
    Endpoint identityEndpoint

    TestProperties properties
    def logFile
    ReposeConfigurationProvider reposeConfigProvider
    ReposeValveLauncher repose

    def setup() {

        // get ports
        properties = new TestProperties()
        reposePort = properties.reposePort
        reposeStopPort = properties.reposeShutdownPort
        originServicePort = properties.targetPort
        identityServicePort = properties.identityPort

        identityService = new MockIdentityService(identityServicePort, originServicePort)

        // start deproxy
        deproxy = new Deproxy()
        originEndpoint = deproxy.addEndpoint(originServicePort)
        identityEndpoint = deproxy.addEndpoint(identityServicePort,
                "identity", "localhost", identityService.handler)

        // configure and start repose

        def targetHostname = properties.targetHostname
        urlBase = "http://${targetHostname}:${reposePort}"
        logFile = properties.logFile

        def configDirectory = properties.configDirectory
        def configTemplates = properties.configTemplates
        reposeConfigProvider = new ReposeConfigurationProvider(configDirectory, configTemplates)

        repose = new ReposeValveLauncher(
                reposeConfigProvider,
                properties.reposeJar,
                urlBase,
                configDirectory,
                reposePort,
                reposeStopPort
        )
        repose.enableDebug()

        def params = properties.getDefaultTemplateParams()
        reposeConfigProvider.applyConfigs("common", params)
        reposeConfigProvider.applyConfigs("features/filters/clientauthn/connectionpooling", params)
        reposeConfigProvider.applyConfigs("features/filters/clientauthn/connectionpooling2", params)
        repose.start()
    }

    def "when a client makes requests, Repose should re-use the connection to the Identity service"() {

        setup: "craft an url to a resource that requires authentication"
        def url = "${urlBase}/servers/tenantid/resource"


        when: "making two authenticated requests to Repose"
        def mc1 = deproxy.makeRequest(url: url, headers: ['X-Auth-Token': 'token1'])
        def mc2 = deproxy.makeRequest(url: url, headers: ['X-Auth-Token': 'token2'])
        // collect all of the handlings that make it to the identity endpoint into one list
        def allOrphanedHandlings = mc1.orphanedHandlings + mc2.orphanedHandlings
        List<Handling> identityHandlings = allOrphanedHandlings.findAll { it.endpoint == identityEndpoint }
        def commons = allOrphanedHandlings.intersect(identityHandlings)
        def diff = allOrphanedHandlings.plus(identityHandlings)
        diff.removeAll(commons)


        then: "the connections for Repose's request to Identity should have the same id"

        mc1.orphanedHandlings.size() > 0
        mc2.orphanedHandlings.size() > 0
        identityHandlings.size() > 0
        // there should be no requests to auth with a different connection id
        diff.size() == 0
    }

    def cleanup() {

        if (repose && repose.isUp()) {
            repose.stop()
        }

        if (deproxy) {
            deproxy.shutdown()
        }
    }
}
