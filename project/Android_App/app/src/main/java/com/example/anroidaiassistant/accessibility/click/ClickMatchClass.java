package com.example.anroidaiassistant.accessibility.click;

enum ClickMatchClass {
    NONE(0),
    TOKEN_OVERLAP(1),
    TOKEN_COVERAGE(2),
    FUZZY(3),
    ALL_TOKENS(4),
    CONTAINS(5),
    EXACT(6);

    private final int priority;

    ClickMatchClass(int priority) {
        this.priority = priority;
    }

    int priority() {
        return priority;
    }

    static ClickMatchClass fromReason(String reason) {
        if (reason == null) {
            return NONE;
        }
        switch (reason) {
            case "exact":
                return EXACT;
            case "contains":
                return CONTAINS;
            case "all_tokens":
                return ALL_TOKENS;
            case "fuzzy":
                return FUZZY;
            case "token_coverage":
                return TOKEN_COVERAGE;
            case "token_overlap":
            case "single_token":
                return TOKEN_OVERLAP;
            default:
                return NONE;
        }
    }
}
