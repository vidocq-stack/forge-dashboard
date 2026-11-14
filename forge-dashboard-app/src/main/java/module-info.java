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
    requires io.vidocq.runtime.extensions.microprofile.cervantes;
    requires java.net.http;
    requires io.vidocq.runtime.extensions.jakartaee.web.mansart.pool;
    requires io.vidocq.runtime.extensions.jakartaee.web.mansart.data;
    requires io.vidocq.runtime.extensions.jakartaee.web.mansart.transactions;
    requires io.vidocq.runtime.extensions.essentials.migration.flyway;
    // VidocqBootstrap, for the tests that start the application in process.
    requires io.vidocq.runtime.core;
    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.json.bind;
    requires jakarta.persistence;
    requires jakarta.data;
    requires jakarta.transaction;
    requires java.sql;
    // The Mansart Data processor needs javax.annotation.processing.Generated at compile time only.
    requires static java.compiler;

    // The Vaadin jars have no module-info: their module names come from their file names.
    requires flow.server;
    requires flow.html.components;
    requires vaadin.ordered.layout.flow;

    // Vaadin creates the routes by reflection.
    opens io.vidocq.tools.dashboard.ui;
    opens io.vidocq.tools.dashboard.auth;
    // JSON-B (Champollion) binds the raw Forgejo records by reflection.
    opens io.vidocq.tools.dashboard.forge.cyrano;
    opens io.vidocq.tools.dashboard.sonar.cyrano;
    // Mansart Data reads and writes the entities, and the enums of their columns, by reflection.
    opens io.vidocq.tools.dashboard.store;
    opens io.vidocq.tools.dashboard.domain;
    opens io.vidocq.tools.dashboard.sonar;
    // Flyway finds the SQL migrations as resources of this module.
    opens db.migration;
}
