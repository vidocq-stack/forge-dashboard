package io.vidocq.tools.dashboard.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparklineTest {

    @Test
    void sparklineScalesToHeight() {
        String svg = Sparkline.svg(List.of(0.0, 1.0), 20, 10);
        assertTrue(svg.startsWith("<svg"), svg);
        assertTrue(svg.contains("0,10 20,0"), svg);
    }

    @Test
    void valuesAreClampedBetweenZeroAndOne() {
        assertTrue(Sparkline.svg(List.of(-1.0, 2.0), 20, 10).contains("0,10 20,0"));
    }

    @Test
    void halfwayIsInTheMiddle() {
        assertTrue(Sparkline.svg(List.of(0.5, 0.5, 0.5), 20, 10).contains("0,5 10,5 20,5"));
    }

    @Test
    void noValueDrawsNoLine() {
        String svg = Sparkline.svg(List.of(), 20, 10);
        assertTrue(svg.startsWith("<svg"), svg);
        assertFalse(svg.contains("points"), svg);
    }
}
