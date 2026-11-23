package io.vidocq.tools.dashboard.forge.cyrano;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * The Forgejo API v1 ({@code forgejo/mp-rest/url}). Every method returns the raw {@link Response}: the adapter reads
 * the status, the {@code Link} and {@code Retry-After} headers, and maps the body with {@link ForgejoJson}.
 */
@RegisterRestClient(configKey = "forgejo")
@RegisterProvider(ForgejoAuthFilter.class)
@Produces(MediaType.APPLICATION_JSON)
public interface ForgejoClient {

    @GET
    @Path("/orgs/{org}/repos")
    Response repos(@PathParam("org") String org, @QueryParam("page") int page, @QueryParam("limit") int limit);

    @GET
    @Path("/orgs/{org}/actions/runners")
    Response runners(@PathParam("org") String org);

    @GET
    @Path("/orgs/{org}/actions/runners/jobs")
    Response activeJobs(@PathParam("org") String org);

    @GET
    @Path("/repos/{owner}/{repo}/actions/runs/{run}")
    Response run(@PathParam("owner") String owner, @PathParam("repo") String repo, @PathParam("run") long run);

    @GET
    @Path("/repos/{owner}/{repo}/actions/jobs/{job}")
    Response job(@PathParam("owner") String owner, @PathParam("repo") String repo, @PathParam("job") long job);

    @GET
    @Path("/repos/{owner}/{repo}/pulls")
    Response pulls(@PathParam("owner") String owner, @PathParam("repo") String repo,
                   @QueryParam("state") String state, @QueryParam("sort") String sort,
                   @QueryParam("page") int page, @QueryParam("limit") int limit);

    @GET
    @Path("/repos/{owner}/{repo}/pulls/{number}/files")
    Response pullFiles(@PathParam("owner") String owner, @PathParam("repo") String repo,
                       @PathParam("number") int number, @QueryParam("page") int page, @QueryParam("limit") int limit);

    /** A file of the repository at the root, raw ({@code components.json}). */
    @GET
    @Path("/repos/{owner}/{repo}/raw/{file}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM})
    Response rawFile(@PathParam("owner") String owner, @PathParam("repo") String repo, @PathParam("file") String file,
                     @QueryParam("ref") String ref);

    @GET
    @Path("/repos/{owner}/{repo}/commits/{ref}/status")
    Response status(@PathParam("owner") String owner, @PathParam("repo") String repo, @PathParam("ref") String ref,
                    @QueryParam("limit") int limit);

    @GET
    @Path("/repos/{owner}/{repo}/commits")
    Response commits(@PathParam("owner") String owner, @PathParam("repo") String repo,
                     @QueryParam("sha") String branch, @QueryParam("stat") boolean stat,
                     @QueryParam("files") boolean files, @QueryParam("verification") boolean verification,
                     @QueryParam("page") int page, @QueryParam("limit") int limit);

    @GET
    @Path("/repos/{owner}/{repo}/commits/{sha}/pull")
    Response commitPull(@PathParam("owner") String owner, @PathParam("repo") String repo,
                        @PathParam("sha") String sha);
}
