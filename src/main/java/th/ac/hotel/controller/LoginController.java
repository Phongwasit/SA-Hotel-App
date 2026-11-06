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
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import th.ac.hotel.security.StaffPrincipal;

import java.util.List;
import java.util.Map;

@Controller
public class LoginController {
    private static final Logger log = LoggerFactory.getLogger(LoginController.class);
    // Keep existing employee IDs and account names for historical records.
    private static final Map<String, String> STAFF_CHOICES = Map.of(
            "reception1", "reception01", "reception2", "reception02", "reception3", "reception03");
    static final String QUERY_AUTH_1 = """
            SELECT employee_id,first_name,last_name,username
            FROM employee WHERE username=?
            """;
    private final JdbcTemplate jdbcTemplate;
    private final SecurityContextRepository contexts;

    public LoginController(JdbcTemplate jdbcTemplate, SecurityContextRepository contexts) {
        this.jdbcTemplate = jdbcTemplate;
        this.contexts = contexts;
    }

    @GetMapping("/login")
    public String loginPage(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof StaffPrincipal ? "redirect:/" : "login";
    }

    @PostMapping("/login")
    public String selectStaff(@RequestParam(defaultValue = "") String employee,
                              HttpServletRequest request, HttpServletResponse response, Model model) {
        String username = STAFF_CHOICES.get(employee);
        if (username == null) {
            model.addAttribute("loginError", "กรุณาเลือกพนักงานจากรายการ");
            return "login";
        }
        try {
            var accounts = jdbcTemplate.queryForList(QUERY_AUTH_1, username);
            if (accounts.size() != 1) {
                model.addAttribute("loginError", "ไม่พบข้อมูลพนักงานที่เลือก กรุณาเปิดโปรแกรมใหม่แล้วลองอีกครั้ง");
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
            log.warn("Staff selection database lookup failed: {}", e.getClass().getSimpleName());
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            model.addAttribute("loginError", "เชื่อมต่อฐานข้อมูลไม่ได้ กรุณาตรวจว่าฐานข้อมูลเปิดอยู่แล้วลองอีกครั้ง");
            return "login";
        }
    }
}
