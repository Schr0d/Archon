package com.archon.core.analysis;

import com.archon.core.graph.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiskScorerTest {

    private final RiskScorer scorer = new RiskScorer();

    @Test
    void computeRisk_allLow_returnsLOW() {
        RiskLevel risk = scorer.computeRisk(2, 1, 1, false, false, false);
        assertEquals(RiskLevel.LOW, risk);
    }

    @Test
    void computeRisk_couplingAbove10_returnsHIGH() {
        RiskLevel risk = scorer.computeRisk(11, 1, 1, false, false, false);
        assertEquals(RiskLevel.HIGH, risk);
    }

    @Test
    void computeRisk_couplingBetween5and10_returnsMEDIUM() {
        RiskLevel risk = scorer.computeRisk(7, 1, 1, false, false, false);
        assertEquals(RiskLevel.MEDIUM, risk);
    }

    @Test
    void computeRisk_crossDomainAbove3_returnsHIGH() {
        RiskLevel risk = scorer.computeRisk(2, 3, 1, false, false, false);
        assertEquals(RiskLevel.HIGH, risk);
    }

    @Test
    void computeRisk_callDepthAbove3_returnsHIGH() {
        RiskLevel risk = scorer.computeRisk(2, 1, 3, false, false, false);
        assertEquals(RiskLevel.HIGH, risk);
    }

    @Test
    void computeRisk_criticalPath_returnsHIGH() {
        RiskLevel risk = scorer.computeRisk(2, 1, 1, true, false, false);
        assertEquals(RiskLevel.HIGH, risk);
    }

    @Test
    void computeRisk_inCycle_returnsVERY_HIGH() {
        RiskLevel risk = scorer.computeRisk(2, 1, 1, false, true, false);
        assertEquals(RiskLevel.VERY_HIGH, risk);
    }

    @Test
    void computeRisk_cycleOverridesHighCoupling() {
        RiskLevel risk = scorer.computeRisk(15, 1, 1, false, true, false);
        assertEquals(RiskLevel.VERY_HIGH, risk);
    }

    @Test
    void computeRisk_lowConfidence_escalatesOneLevel() {
        RiskLevel risk = scorer.computeRisk(7, 1, 1, false, false, true);
        assertEquals(RiskLevel.HIGH, risk);
    }

    @Test
    void computeRisk_lowConfidence_escalatesHIGHToVERY_HIGH() {
        RiskLevel risk = scorer.computeRisk(11, 1, 1, false, false, true);
        assertEquals(RiskLevel.VERY_HIGH, risk);
    }

    @Test
    void computeRisk_lowConfidence_escalatesVERY_HIGHToBLOCKED() {
        RiskLevel risk = scorer.computeRisk(2, 1, 1, false, true, true);
        assertEquals(RiskLevel.BLOCKED, risk);
    }

    @Test
    void computeRisk_lowConfidence_LOWStaysLOW() {
        RiskLevel risk = scorer.computeRisk(2, 1, 1, false, false, true);
        assertEquals(RiskLevel.LOW, risk);
    }

    @Test
    void computeRisk_multipleHIGHConditions_returnsHIGH() {
        RiskLevel risk = scorer.computeRisk(11, 1, 1, true, false, false);
        assertEquals(RiskLevel.HIGH, risk);
    }

    @Test
    void computeRisk_couplingAtExactThreshold5_returnsMEDIUM() {
        RiskLevel risk = scorer.computeRisk(5, 1, 1, false, false, false);
        assertEquals(RiskLevel.MEDIUM, risk);
    }

    @Test
    void computeRisk_couplingAt4_returnsLOW() {
        RiskLevel risk = scorer.computeRisk(4, 1, 1, false, false, false);
        assertEquals(RiskLevel.LOW, risk);
    }
}