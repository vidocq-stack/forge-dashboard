package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

/** Provisional landing view, replaced by the dashboard layout in a later task. */
@Route("")
public class MainView extends VerticalLayout {

    public MainView() {
        add(new H1("forge-dashboard"));
    }
}
