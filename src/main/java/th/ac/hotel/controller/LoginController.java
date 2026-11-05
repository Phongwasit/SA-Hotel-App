package th.ac.hotel.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import th.ac.hotel.security.StaffPrincipal;

import java.util.List;
import java.nio.charset.StandardCharsets;

@Controller
public class LoginController {
    private static final Logger log = LoggerFactory.getLogger(LoginController.class);
    // queryAUTH_1: parameters remain bound data, never concatenated into SQL.
    static final String QUERY_AUTH_1 = """
            SELECT employee_id,first_name,last_name,username,password_hash
            FROM employee WHERE username=?
            """;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository contexts;
    private final String dummyHash;

    public LoginController(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, SecurityContextRepository contexts) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.contexts = contexts;
        this.dummyHash = passwordEncoder.encode("no-such-account");
    }

    @GetMapping("/login")
    public String loginPage(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof StaffPrincipal ? "redirect:/" : "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam(defaultValue = "") String username,
                        @RequestParam(defaultValue = "") String password,
                        HttpServletRequest request, HttpServletResponse response, Model model) {
        String name = username.strip();
        model.addAttribute("username", name.length() <= 50 ? name : "");
        if (name.isEmpty() || name.length() > 50 || password.isEmpty() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            model.addAttribute("loginError", "ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง");
            return "login";
        }
        try {
            var accounts = jdbcTemplate.queryForList(QUERY_AUTH_1, name);
            String hash = accounts.isEmpty() ? dummyHash : (String) accounts.getFirst().get("password_hash");
            boolean matches = passwordEncoder.matches(password, hash);
            if (accounts.size() != 1 || !matches) {
                model.addAttribute("loginError", "ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง");
                return "login";
            }
            var account = accounts.getFirst();
            var staff = new StaffPrincipal(((Number) account.get("employee_id")).intValue(),
                    (String) account.get("username"), account.get("first_name") + " " + account.get("last_name"));
            request.getSession(true);
            request.changeSessionId();
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    staff, null, List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, request, response);
            request.getSession().setAttribute("employeeId", staff.employeeId());
            return "redirect:/";
        } catch (DataAccessException e) {
            log.warn("Staff login database lookup failed: {}", e.getClass().getSimpleName());
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            model.addAttribute("loginError", "เชื่อมต่อฐานข้อมูลไม่ได้ กรุณาตรวจว่าฐานข้อมูลเปิดอยู่แล้วลองอีกครั้ง");
            return "login";
        }
    }
}
