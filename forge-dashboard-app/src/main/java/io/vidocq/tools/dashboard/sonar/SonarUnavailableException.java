package io.vidocq.tools.dashboard.sonar;

/** Sonar could not be reached, or answered an error other than 404. */
public final class SonarUnavailableException extends RuntimeException {

    public SonarUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
