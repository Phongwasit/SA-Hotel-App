package th.ac.hotel.ui;

import java.util.*;
import org.springframework.ui.Model;
import th.ac.hotel.controller.RoomStatusController.RoomStatusResult;
public final class RoomStatusUI {
    public static void displayRoomStatus(RoomStatusResult result,Model model) {model.addAttribute("roomStatusResult",result);model.addAttribute("selectedRoom",result.room());}
    public static String displayRoomStatusResult(RoomStatusResult result) {return "redirect:/room-status?roomId="+result.room().roomId()+"&saved=true";}
}
