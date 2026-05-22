/**
 * forge-dashboard — runtime Vidocq qui expose un dashboard JAX-RS server-rendered
 * (HTMX) sur les projets et runners de la forge Forgejo.
 */
module io.vidocq.tools.dashboard.app {
    requires java.net.http;
    requires java.logging;

    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.annotation;
    requires jakarta.ws.rs;
    requires jakarta.json;
    requires jakarta.json.bind;

    requires io.vidocq.runtime.core;
    requires io.vidocq.runtime.spi;
    requires io.vidocq.runtime.ext.rest.cassini;
    requires io.vidocq.chappe.api;
    requires io.vidocq.vauban.core;

    // JAX-RS et CDI font de la reflection sur les ressources, services et records.
    opens io.vidocq.tools.dashboard.web;
    opens io.vidocq.tools.dashboard.forgejo;
    opens io.vidocq.tools.dashboard.service;
    opens io.vidocq.tools.dashboard.model;
}
