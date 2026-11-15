package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import io.vidocq.tools.dashboard.auth.DashboardUser;
import io.vidocq.tools.dashboard.collect.OrgAdmin;
import io.vidocq.tools.dashboard.collect.OrgAdminResult;
import io.vidocq.tools.dashboard.i18n.Messages;

import java.util.function.Consumer;

/** Opened by the « + » tab: the name of a CodeFloe org to track, and why it was refused if it was. */
final class OrgAdminDialog extends Dialog {

    OrgAdminDialog(OrgAdmin admin, DashboardUser user, Consumer<String> onAdded) {
        Messages messages = Texts.current();
        setHeaderTitle(messages.text("orgs.dialogTitle"));
        TextField name = new TextField(messages.text("orgs.name"));
        name.setPlaceholder("AkoyoGames");
        name.setWidthFull();
        name.setAutofocus(true);
        Span refused = new Span();
        refused.getStyle().set("color", "var(--lumo-error-text-color)").set("font-size", "var(--lumo-font-size-s)");
        Span hint = new Span(messages.text("orgs.hint"));
        hint.addClassName(Theme.MUTED);
        hint.getStyle().set("font-size", "var(--lumo-font-size-s)");
        VerticalLayout body = new VerticalLayout(name, hint, refused);
        body.setPadding(false);
        body.setSpacing(false);
        body.setWidth("24rem");
        body.setMaxWidth("100%");
        add(body);

        Button add = new Button(messages.text("orgs.add"), e -> {
            switch (admin.add(name.getValue(), user)) {
                case OrgAdminResult.Added added -> {
                    close();
                    onAdded.accept(added.org());
                }
                case OrgAdminResult.Rejected rejected -> refused.setText(messages.text(rejected.key(), rejected.args().toArray()));
            }
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.addClickShortcut(Key.ENTER);
        getFooter().add(new Button(messages.text("orgs.cancel"), e -> close()), add);
    }
}
