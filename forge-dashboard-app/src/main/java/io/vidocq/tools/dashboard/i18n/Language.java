package io.vidocq.tools.dashboard.i18n;

/**
 * A language of the dashboard.
 *
 * @param tag  its IETF tag ({@code fr}), the suffix of its translation file
 * @param name its name written in itself ({@code Français}), as the language menu shows it
 */
public record Language(String tag, String name) {
}
