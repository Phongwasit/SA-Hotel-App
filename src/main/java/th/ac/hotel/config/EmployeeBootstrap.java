package th.ac.hotel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Provisions local demonstration accounts once. Never resets an existing account. */
@Component
@ConditionalOnProperty(name = "hotel.bootstrap.enabled", havingValue = "true")
public class EmployeeBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String firstPassword;
    private final String secondPassword;

    public EmployeeBootstrap(JdbcTemplate jdbc, PasswordEncoder encoder,
                             @Value("${hotel.bootstrap.staff01-password}") String firstPassword,
                             @Value("${hotel.bootstrap.staff02-password}") String secondPassword) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.firstPassword = firstPassword;
        this.secondPassword = secondPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing("reception01", "พนักงานทดสอบ", "01", firstPassword);
        createIfMissing("reception02", "พนักงานทดสอบ", "02", secondPassword);
    }

    private void createIfMissing(String username, String firstName, String lastName, String password) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM employee WHERE username=?", Integer.class, username) > 0) return;
        if (password.length() < 12) throw new IllegalStateException("Initial staff passwords must be configured before provisioning.");
        jdbc.update("INSERT INTO employee(first_name,last_name,position,username,password_hash) VALUES (?,?,?,?,?)",
                firstName, lastName, "Front Desk", username, encoder.encode(password));
    }
}
