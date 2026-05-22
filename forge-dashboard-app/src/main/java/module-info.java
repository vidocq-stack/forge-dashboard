/**
 * forge-dashboard — runtime Vidocq qui expose un dashboard JAX-RS server-rendered
 * (HTMX) sur les projets et runners de la forge Forgejo.
 */
module io.vidocq.tools.dashboard.app {
    requires java.net.http;

    requires jakarta.ws.rs;
    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.annotation;
    requires jakarta.json.bind;

    requires io.vidocq.runtime.core;
    requires io.vidocq.runtime.cassini.rest.extension;
    requires io.vidocq.champollion.jsonb;

    exports io.vidocq.tools.dashboard;
    exports io.vidocq.tools.dashboard.web;
    exports io.vidocq.tools.dashboard.model;
    exports io.vidocq.tools.dashboard.forgejo;

    opens io.vidocq.tools.dashboard.model to jakarta.json.bind;
}
