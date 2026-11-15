package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import io.vidocq.tools.dashboard.i18n.Messages;

/** The small building blocks the panels and drawers share. */
final class Parts {

    private Parts() {
    }

    /** A CodeFloe page, in a new tab. */
    static Anchor link(String text, String url) {
        Anchor a = new Anchor(url, text);
        a.setTarget(AnchorTarget.BLANK);
        return a;
    }

    /** A text that opens something in the dashboard (a drawer). */
    static Button action(String text, Runnable onClick) {
        Button b = new Button(text, e -> onClick.run());
        b.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        return b;
    }

    static HorizontalLayout line(Component... parts) {
        HorizontalLayout line = new HorizontalLayout(parts);
        line.setAlignItems(FlexComponent.Alignment.BASELINE);
        line.setSpacing(false);
        line.getStyle().set("gap", "var(--lumo-space-s)").set("flex-wrap", "wrap");
        return line;
    }

    static H4 section(String title) {
        H4 h = new H4(title);
        h.getStyle().set("margin", "var(--lumo-space-m) 0 var(--lumo-space-xs)");
        return h;
    }

    static Span muted(String text) {
        Span s = new Span(text);
        s.getStyle().set("color", "var(--lumo-secondary-text-color)");
        return s;
    }

    /** A right-hand panel over the page, hidden until opened. */
    static void drawer(Div div) {
        div.getStyle().set("position", "fixed").set("top", "0").set("right", "0").set("height", "100%")
                .set("width", "min(30rem, 100%)").set("box-sizing", "border-box").set("overflow-y", "auto")
                .set("padding", "var(--lumo-space-m)").set("z-index", "100")
                .set("background", "var(--lumo-base-color)").set("box-shadow", "var(--lumo-box-shadow-l)");
        div.setVisible(false);
    }

    /** The heading of a drawer, with its close button. */
    static HorizontalLayout drawerTop(String title, Runnable close, Messages m) {
        H3 heading = new H3(title);
        heading.getStyle().set("margin", "0").set("flex", "1");
        Button x = new Button("✕", e -> close.run());
        x.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        x.getElement().setAttribute("aria-label", m.text("common.close"));
        HorizontalLayout top = new HorizontalLayout(heading, x);
        top.setWidthFull();
        top.setAlignItems(FlexComponent.Alignment.CENTER);
        return top;
    }
}
