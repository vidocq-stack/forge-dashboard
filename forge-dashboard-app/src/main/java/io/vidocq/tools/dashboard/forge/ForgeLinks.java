package io.vidocq.tools.dashboard.forge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** The pages of the forge the dashboard links to. */
@ApplicationScoped
public class ForgeLinks {

    @Inject
    @ConfigProperty(name = "FORGEJO_URL", defaultValue = "https://codefloe.com")
    String forgeUrl;

    public ForgeLinks() {
    }

    /** For the tests. */
    public ForgeLinks(String forgeUrl) {
        this.forgeUrl = forgeUrl;
    }

    public String repo(String owner, String repo) {
        return forgeUrl.replaceAll("/+$", "") + "/" + owner + "/" + repo;
    }
}
