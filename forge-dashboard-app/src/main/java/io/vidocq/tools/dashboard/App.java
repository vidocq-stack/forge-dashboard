package io.vidocq.tools.dashboard;

import io.vidocq.runtime.core.Vidocq;

import java.io.IOException;
import java.util.logging.LogManager;

public final class App {

    private App() {}

    public static void main(String[] args) throws IOException {
        try (var in = App.class.getResourceAsStream("/logging.properties")) {
            if (in != null) LogManager.getLogManager().readConfiguration(in);
        }
        Vidocq.main(args);
    }
}
