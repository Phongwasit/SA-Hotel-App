package th.ac.hotel.ui;

import java.util.*;
import org.springframework.ui.Model;
import th.ac.hotel.controller.CheckInController.CheckInResult;
public final class CheckInUI {
    public static String displayCheckInResult(CheckInResult result) {return "redirect:/stays/"+result.stayIds().getFirst()+"?checkedIn="+result.stayIds().size();}
}
