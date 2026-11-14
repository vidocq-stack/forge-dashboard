package io.vidocq.tools.dashboard.ui.cdi;

import com.vaadin.flow.di.DefaultInstantiator;
import com.vaadin.flow.server.VaadinService;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

/**
 * Vaadin's instantiator, with the CDI beans of the application taken from Vauban: a route or a component that is a
 * bean ({@code @Dependent}) gets its injections; any other class is created by Vaadin as before.
 */
public class VaubanInstantiator extends DefaultInstantiator {

    public VaubanInstantiator(VaadinService service) {
        super(service);
    }

    @Override
    public <T> T getOrCreate(Class<T> type) {
        Instance<T> bean = CDI.current().select(type);
        return bean.isResolvable() ? bean.get() : super.getOrCreate(type);
    }
}
