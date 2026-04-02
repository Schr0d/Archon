package com.archon.core.config;

import java.util.Objects;

/**
 * Represents a rule violation found during validation.
 */
public class RuleViolation {
    private final String rule;
    private final String severity;   // "ERROR" or "WARNING"
    private final String details;

    public RuleViolation(String rule, String severity, String details) {
        this.rule = Objects.requireNonNull(rule);
        this.severity = Objects.requireNonNull(severity);
        this.details = Objects.requireNonNull(details);
    }

    public String getRule() { return rule; }
    public String getSeverity() { return severity; }
    public String getDetails() { return details; }

    @Override
    public String toString() {
        return severity + ": " + rule + " — " + details;
    }
}
