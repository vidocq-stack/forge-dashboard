package io.vidocq.tools.dashboard.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OidcFilterTest {

    /**
     * The filter sits in front of {@code /VAADIN/push}: a chain is async only if every filter in it is, and Atmosphere
     * suspends each long-polling request with {@code startAsync} (else "async not supported", a 503 every 5 s).
     */
    @Test
    void filterSupportsAsyncForThePushChain() {
        assertTrue(OidcFilter.class.getAnnotation(WebFilter.class).asyncSupported());
    }

    /**
     * A page whose session is gone (a restart, a cookie another localhost app replaced) keeps sending its messages,
     * heartbeats and push polls. Vaadin answers them "session expired" and the page reloads, which starts the login: a
     * 401 instead left it on "Connection lost", reconnecting for ever.
     */
    @Test
    void vaadinMessagesWithoutLoginReachVaadinWhichAnswersSessionExpired() throws Exception {
        for (String[] request : new String[][]{{"/", "uidl"}, {"/", "heartbeat"}, {"/VAADIN/push", "push"}}) {
            Exchange exchange = new Exchange(request[0], request[1]);

            new OidcFilter().doFilter(exchange.request(), exchange.response(), exchange.chain());

            assertEquals(List.of("chain"), exchange.calls, request[1]);
        }
    }

    /** Only these three cannot open a Vaadin session: a bootstrap ({@code init}) or another path stays refused. */
    @Test
    void otherVaadinRequestsWithoutLoginAreRefused() throws Exception {
        for (String[] request : new String[][]{{"/", "init"}, {"/", "webcomponent-resync"}, {"/", "push"},
                {"/VAADIN/push", "uidl"}}) {
            Exchange exchange = new Exchange(request[0], request[1]);

            new OidcFilter().doFilter(exchange.request(), exchange.response(), exchange.chain());

            assertEquals(List.of("sendError 401"), exchange.calls, request[0] + " " + request[1]);
        }
    }

    /** A request without session, with {@code v-r} in its query: what the filter does with it is in {@link #calls}. */
    private static final class Exchange {

        private final String path;
        private final String type;
        final List<String> calls = new ArrayList<>();

        Exchange(String path, String type) {
            this.path = path;
            this.type = type;
        }

        HttpServletRequest request() {
            return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getRequestURI" -> path;
                        case "getContextPath" -> "";
                        case "getQueryString" -> "v-r=" + type + "&v-uiId=0";
                        case "getParameter" -> "v-r".equals(args[0]) ? type : null;
                        case "getMethod" -> "POST";
                        case "getSession", "getHeader" -> null;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        HttpServletResponse response() {
            return (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class}, (proxy, method, args) -> {
                        calls.add(method.getName() + (args == null ? "" : " " + args[0]));
                        return null;
                    });
        }

        FilterChain chain() {
            return (request, response) -> calls.add("chain");
        }
    }
}
