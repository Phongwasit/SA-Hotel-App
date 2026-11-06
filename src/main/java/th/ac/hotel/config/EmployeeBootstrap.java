package th.ac.hotel.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Provisions local staff choices once. Never resets an existing employee. */
@Component
@ConditionalOnProperty(name = "hotel.bootstrap.enabled", havingValue = "true")
public class EmployeeBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public EmployeeBootstrap(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing("reception01", "พนักงานต้อนรับ", "01");
        createIfMissing("reception02", "พนักงานต้อนรับ", "02");
        createIfMissing("reception03", "พนักงานต้อนรับ", "03");
    }

    private void createIfMissing(String username, String firstName, String lastName) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM employee WHERE username=?", Integer.class, username) > 0) return;
        // Retain the existing schema; staff selection does not use passwords.
        jdbc.update("INSERT INTO employee(first_name,last_name,position,username,password_hash) VALUES (?,?,?,?,?)",
                firstName, lastName, "Front Desk", username, encoder.encode(UUID.randomUUID().toString()));
    }
}
