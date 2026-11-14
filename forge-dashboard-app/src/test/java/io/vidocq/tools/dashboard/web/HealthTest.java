package io.vidocq.tools.dashboard.web;

import io.vidocq.runtime.junit.VidocqApplication;
import io.vidocq.runtime.junit.VidocqTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@VidocqTest(properties = {"vaadin.productionMode=true", "dashboard.collect.enabled=false"})
class HealthTest {

    @Test
    void healthzAnswersOk(VidocqApplication app) {
        var r = app.get("/healthz");
        assertEquals(200, r.status(), app.log());
        assertEquals("OK", r.body().strip());
    }

    @Test
    void rootServesVaadinPage(VidocqApplication app) {
        var r = app.get("/");
        assertEquals(200, r.status(), app.log());
        assertTrue(r.body().contains("<html"), r.body());
    }
}
