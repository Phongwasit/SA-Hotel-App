package th.ac.hotel.ui;

import java.util.*;
import org.springframework.ui.Model;
import th.ac.hotel.controller.CheckOutController.*;
public final class CheckOutUI {
    public static void displayCheckOutDetails(CheckOutDetailsResult result,Model model) {model.addAttribute("accounts",result.accounts());}
    public static String displayCheckOutResult(CheckOutResult result) {return "redirect:/stays/"+result.stayId()+"?checkedOut=true";}
}
