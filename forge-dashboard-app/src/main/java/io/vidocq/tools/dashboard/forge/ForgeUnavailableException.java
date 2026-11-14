package io.vidocq.tools.dashboard.forge;

/** The forge could not be reached, or answered an error other than 404, 409 and 429. */
public final class ForgeUnavailableException extends RuntimeException {

    public ForgeUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
