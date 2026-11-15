package io.vidocq.tools.dashboard.ui.cdi;

import com.vaadin.flow.di.DefaultInstantiator;
import com.vaadin.flow.i18n.I18NProvider;
import com.vaadin.flow.server.VaadinService;
import io.vidocq.tools.dashboard.ui.DashboardI18NProvider;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

/**
 * Vaadin's instantiator, with the CDI beans of the application taken from Vauban: a route or a component that is a
 * bean ({@code @Dependent}) gets its injections; any other class is created by Vaadin as before.
 */
public class VaubanInstantiator extends DefaultInstantiator {

    private final I18NProvider i18n = new DashboardI18NProvider();

    public VaubanInstantiator(VaadinService service) {
        super(service);
    }

    /** The dashboard's languages: Vaadin picks the UI language among them from the browser. */
    @Override
    public I18NProvider getI18NProvider() {
        return i18n;
    }

    @Override
    public <T> T getOrCreate(Class<T> type) {
        Instance<T> bean = CDI.current().select(type);
        return bean.isResolvable() ? bean.get() : super.getOrCreate(type);
    }
}
