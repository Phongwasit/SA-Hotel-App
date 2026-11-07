package th.ac.hotel.ui;

import java.util.*;
import org.springframework.ui.Model;
import th.ac.hotel.controller.GuestController.ProfileResult;
public final class GuestUI {
    public static void displayProfiles(List<Map<String,Object>> matches,Model model) {model.addAttribute("profiles",matches);}
    public static void selectProfile(Map<String,Object> profile,Map<String,Object> fields,Object profileId,Model model) {
        model.addAttribute("profile",profile);model.addAttribute("input",fields);model.addAttribute("profileId",profileId);
    }
    public static void displaySaveResult(ProfileResult result,Model model) {
        model.addAttribute("profileResult",result);if(!result.validationErrors().isEmpty())model.addAttribute("formError",result.validationErrors().getFirst());
    }
}
