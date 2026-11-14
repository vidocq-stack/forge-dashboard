package io.vidocq.tools.dashboard.sonar.cyrano;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/** The SonarQube Web API ({@code sonar/mp-rest/url}). */
@RegisterRestClient(configKey = "sonar")
@RegisterProvider(SonarAuthFilter.class)
@Produces(MediaType.APPLICATION_JSON)
public interface SonarClient {

    @GET
    @Path("/api/measures/component")
    Response measures(@QueryParam("component") String key, @QueryParam("metricKeys") String metricKeys);

    @GET
    @Path("/api/qualitygates/project_status")
    Response gate(@QueryParam("projectKey") String key);

    @GET
    @Path("/api/qualitygates/project_status")
    Response pullRequestGate(@QueryParam("projectKey") String key, @QueryParam("pullRequest") int pr);
}
