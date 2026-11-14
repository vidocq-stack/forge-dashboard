/**
 * forge-dashboard: projects, CI runners and committers of the Vidocq CodeFloe organisations, in Vaadin on Vidocq.
 */
module io.vidocq.tools.dashboard {
    requires io.vidocq.runtime.extensions.jakartaee.web.foy;
    requires io.vidocq.foy.api;
    requires io.vidocq.vauban.api;
    requires jakarta.servlet;
    requires io.vidocq.runtime.extensions.jakartaee.web.vaadin;
    requires io.vidocq.runtime.extensions.microprofile.ravel;
    requires io.vidocq.runtime.extensions.microprofile.cyrano;
    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.json.bind;

    // The Vaadin jars have no module-info: their module names come from their file names.
    requires flow.server;
    requires flow.html.components;
    requires vaadin.ordered.layout.flow;

    // Vaadin creates the routes by reflection.
    opens io.vidocq.tools.dashboard.ui;
    // JSON-B (Champollion) binds the raw Forgejo records by reflection.
    opens io.vidocq.tools.dashboard.forge.cyrano;
    opens io.vidocq.tools.dashboard.sonar.cyrano;
}
