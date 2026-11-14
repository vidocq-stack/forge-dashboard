package io.vidocq.tools.dashboard.forge;

import java.util.regex.Pattern;

/** Reads the RFC 8288 {@code Link} header of a paginated Forgejo answer. */
public final class NextPage {

    private static final Pattern NEXT = Pattern.compile("rel=\"?next\"?");

    private NextPage() {
    }

    public static boolean hasNext(String linkHeader) {
        return linkHeader != null && NEXT.matcher(linkHeader).find();
    }
}
