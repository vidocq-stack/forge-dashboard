package io.vidocq.tools.dashboard.ui;

import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/** A line of ratios (0..1) as inline SVG: 0 at the bottom, 1 at the top, the values evenly spaced across. */
public final class Sparkline {

    private Sparkline() {
    }

    public static String svg(List<Double> values, int width, int height) {
        String open = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + width + "\" height=\"" + height
                + "\" viewBox=\"0 0 " + width + " " + height + "\" style=\"overflow:visible\">";
        if (values.isEmpty()) {
            return open + "</svg>";
        }
        List<Double> points = values.size() == 1 ? List.of(values.getFirst(), values.getFirst()) : values;
        StringJoiner line = new StringJoiner(" ");
        for (int i = 0; i < points.size(); i++) {
            double ratio = Math.clamp(points.get(i) == null ? 0.0 : points.get(i), 0.0, 1.0);
            line.add(number((double) i * width / (points.size() - 1)) + "," + number(height - ratio * height));
        }
        return open + "<polyline fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.5\" "
                + "stroke-linejoin=\"round\" points=\"" + line + "\"/></svg>";
    }

    /** {@code 10}, not {@code 10.0}; one decimal otherwise. */
    private static String number(double v) {
        double rounded = Math.round(v * 10) / 10.0;
        return rounded == Math.rint(rounded) ? Long.toString((long) rounded) : String.format(Locale.ROOT, "%.1f", rounded);
    }
}
