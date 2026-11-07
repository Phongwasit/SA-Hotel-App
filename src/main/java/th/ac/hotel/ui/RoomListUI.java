package th.ac.hotel.ui;

import java.util.*;
import org.springframework.ui.Model;
import th.ac.hotel.controller.RoomListController.SearchResult;
public final class RoomListUI {
    public static void displaySearchResult(SearchResult result,Model model) {
        model.addAttribute("searchResult",result);model.addAttribute("matchingRooms",result.matchingRooms());
        model.addAttribute("errors",result.validationErrors());
        if(result.validationErrors().isEmpty()) {
            model.addAttribute("availableCount",result.matchingRooms().size());model.addAttribute("shortfall",result.shortfall());
        } else {
            model.asMap().remove("availableCount");model.asMap().remove("shortfall");
        }
    }
}
