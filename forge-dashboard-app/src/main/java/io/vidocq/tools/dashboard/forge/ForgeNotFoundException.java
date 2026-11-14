package io.vidocq.tools.dashboard.forge;

/** The forge answered 404, or 409 for an empty repository. */
public final class ForgeNotFoundException extends RuntimeException {

    public ForgeNotFoundException(String what) {
        super(what);
    }
}
