package io.vidocq.tools.dashboard.forge.cyrano;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * The Forgejo web routes ({@code forgejo-web/mp-rest/url}): Forgejo 17 serves a job's log there only, with the same
 * token, and honours {@code Range}.
 */
@RegisterRestClient(configKey = "forgejo-web")
@RegisterProvider(ForgejoAuthFilter.class)
public interface ForgejoWebClient {

    @GET
    @Path("/{owner}/{repo}/actions/runs/{run}/jobs/{job}/attempt/{attempt}/logs")
    @Produces(MediaType.TEXT_PLAIN)
    Response log(@PathParam("owner") String owner, @PathParam("repo") String repo, @PathParam("run") long run,
                 @PathParam("job") long job, @PathParam("attempt") long attempt, @HeaderParam("Range") String range);
}
