package th.ac.hotel.model;

/** Name-field rules shared by the controller and native browser patterns. */
public final class NameRules {
    public static final String PERSON_PATTERN = "(?=.*\\p{L})[\\p{L}\\p{M} .'’ʼ‐‑\\-]+";
    public static final String ORGANIZATION_PATTERN = "(?=.*\\p{L})[^\\p{Cc}]+";
    private NameRules() {}
    public static boolean person(String value) { return value != null && value.matches(PERSON_PATTERN); }
    public static boolean organization(String value) { return value != null && value.matches(ORGANIZATION_PATTERN); }
}
