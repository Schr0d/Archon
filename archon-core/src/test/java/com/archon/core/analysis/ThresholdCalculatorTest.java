package com.archon.core.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThresholdCalculatorTest {

    @Test
    void calculate_smallProject_strictThresholds() {
        Thresholds t = ThresholdCalculator.calculate(50, 3);

        assertEquals(3, t.getCouplingThreshold());   // max(3, 50/100) = 3
        assertEquals(2, t.getCrossDomainMax());       // max(2, ceil(3*0.3)) = 2
        assertEquals(4, t.getMaxCallDepth());
        assertEquals(5, t.getHotspotDisplayCap());     // min(20, max(5, 50/10)) = min(20,5) = 5
    }

    @Test
    void calculate_mediumProject_moderateThresholds() {
        Thresholds t = ThresholdCalculator.calculate(200, 5);

        assertEquals(3, t.getCouplingThreshold());   // max(3, 2) = 3
        assertEquals(2, t.getCrossDomainMax());       // max(2, ceil(5*0.3)) = max(2,2) = 2
        assertEquals(20, t.getHotspotDisplayCap());   // min(20, 20) = 20
    }

    @Test
    void calculate_largeProject_relaxedThresholds() {
        Thresholds t = ThresholdCalculator.calculate(1000, 71);

        assertEquals(10, t.getCouplingThreshold());  // max(3, 10) = 10
        assertEquals(22, t.getCrossDomainMax());      // max(2, ceil(71*0.3)) = 22
        assertEquals(20, t.getHotspotDisplayCap());   // min(20, 100) = 20
    }

    @Test
    void calculate_singleDomain_crossDomainMaxFloor() {
        Thresholds t = ThresholdCalculator.calculate(10, 1);

        assertEquals(2, t.getCrossDomainMax());       // floor is 2
    }

    @Test
    void thresholds_hasGetterForAllFields() {
        Thresholds t = new Thresholds(5, 3, 4, 10);

        assertEquals(5, t.getCouplingThreshold());
        assertEquals(3, t.getCrossDomainMax());
        assertEquals(4, t.getMaxCallDepth());
        assertEquals(10, t.getHotspotDisplayCap());
    }
}
