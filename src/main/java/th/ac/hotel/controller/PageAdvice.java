package th.ac.hotel.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import th.ac.hotel.security.StaffPrincipal;

@ControllerAdvice
public class PageAdvice {
    @ModelAttribute("staff")
    public StaffPrincipal staff(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof StaffPrincipal staff ? staff : null;
    }
}
