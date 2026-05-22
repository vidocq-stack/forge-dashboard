package io.vidocq.tools.dashboard.web;

import io.vidocq.tools.dashboard.forgejo.ForgejoClient;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
public class DashboardResource {

    @Inject
    ForgejoClient forgejo;

    @GET
    @Path("healthz")
    @Produces(MediaType.TEXT_PLAIN)
    public String healthz() {
        return "ok";
    }

    /** Fragment HTML — liste des repos. Stub M0 — a remplacer par un rendu reel. */
    @GET
    @Path("tiles/repos")
    @Produces(MediaType.TEXT_HTML)
    public Response reposTile() {
        var html = """
                <div class="tile-repos">
                  <p class="placeholder">repos a venir (FORGEJO_URL + token requis)</p>
                </div>
                """;
        return Response.ok(html).build();
    }

    /** Fragment HTML — etat des runners Actions. Stub M0. */
    @GET
    @Path("tiles/runners")
    @Produces(MediaType.TEXT_HTML)
    public Response runnersTile() {
        var html = """
                <div class="tile-runners">
                  <p class="placeholder">runners a venir</p>
                </div>
                """;
        return Response.ok(html).build();
    }
}
