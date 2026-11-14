package io.vidocq.tools.dashboard.ui.cdi;

import com.vaadin.flow.di.InstantiatorFactory;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import io.vidocq.tools.dashboard.ui.UserPrefs;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaubanInstantiatorTest {

    @Test
    void beanComesFromCdi() {
        InProcessApp.start();
        UserPrefs prefs = new VaubanInstantiator(null).getOrCreate(UserPrefs.class);
        assertInstanceOf(UserPrefs.class, prefs);
    }

    @Test
    void factoryIsAServiceProviderOfTheModule() {
        getClass().getModule().addUses(InstantiatorFactory.class);
        assertTrue(ServiceLoader.load(InstantiatorFactory.class).stream()
                .anyMatch(p -> p.type() == VaubanInstantiatorFactory.class));
    }
}
