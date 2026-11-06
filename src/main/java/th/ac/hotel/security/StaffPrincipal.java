package th.ac.hotel.security;

import java.io.Serializable;
import java.security.Principal;

/** Session identity deliberately excludes password hashes. */
public record StaffPrincipal(int employeeId, String username, String displayName)
        implements Principal, Serializable {
    @Override
    public String getName() { return username; }

    public String getSelectionName() {
        return switch (username) {
            case "reception01" -> "reception1";
            case "reception02" -> "reception2";
            case "reception03" -> "reception3";
            default -> username;
        };
    }
}
