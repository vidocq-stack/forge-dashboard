package io.vidocq.tools.dashboard.ui.cdi;

import com.vaadin.flow.di.Instantiator;
import com.vaadin.flow.di.InstantiatorFactory;
import com.vaadin.flow.server.VaadinService;

/** Found by Vaadin through {@code ServiceLoader} ({@code provides} in {@code module-info.java}). */
public class VaubanInstantiatorFactory implements InstantiatorFactory {

    @Override
    public Instantiator createInstantitor(VaadinService service) {
        return new VaubanInstantiator(service);
    }
}
