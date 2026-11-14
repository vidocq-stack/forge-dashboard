package io.vidocq.tools.dashboard.auth;

/** The tokens of a code exchange. The access token is never used: the data come from the service token. */
public record TokenResponse(String idToken, String accessToken) {

    @Override
    public String toString() {
        return "TokenResponse[idToken=***, accessToken=***]";
    }
}
