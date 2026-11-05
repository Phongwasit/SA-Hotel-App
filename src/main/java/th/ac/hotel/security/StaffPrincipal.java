package th.ac.hotel.security;

import java.io.Serializable;
import java.security.Principal;

/** Session identity deliberately excludes password hashes. */
public record StaffPrincipal(int employeeId, String username, String displayName)
        implements Principal, Serializable {
    @Override
    public String getName() { return username; }
}
