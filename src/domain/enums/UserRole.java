package domain.enums;

import java.util.Set;

/** OOP enum: each role owns its authorization policy. Unknown roles fail closed. */
public enum UserRole {
    OWNER("owner", "เจ้าของ"), ADMIN("admin", "ผู้ดูแล"), STAFF("staff", "พนักงาน");

    private final String code;
    private final String label;
    UserRole(String code, String label) { this.code = code; this.label = label; }
    public String getLabel() { return label; }
    public static UserRole fromCode(String code) {
        for (UserRole role : values()) if (role.code.equals(code)) return role;
        return null;
    }
    private static final Set<String> OPERATIONS = Set.of(
        "/api/status", "/api/lot", "/api/park", "/api/calculate-fee", "/api/pay",
        "/api/exit", "/api/lost-ticket", "/api/tickets", "/api/ai/recommend",
        "/api/ai/anpr", "/api/ai/anpr-entry", "/api/hardware/status");
    private static final Set<String> MANAGEMENT = Set.of(
        "/api/payments", "/api/reservations", "/api/memberships", "/api/history", "/api/hardware/gate");
    private static final Set<String> OWNER_ONLY = Set.of(
        "/api/dashboard/daily", "/api/time-travel", "/api/ai/predict", "/api/ai/insights", "/api/ai/chat");
    public boolean allows(String path) {
        return OPERATIONS.contains(path) || (this != STAFF && MANAGEMENT.contains(path))
            || (this == OWNER && OWNER_ONLY.contains(path));
    }
}
