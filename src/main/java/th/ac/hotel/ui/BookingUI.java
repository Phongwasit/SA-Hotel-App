package th.ac.hotel.ui;

import java.util.*;
import org.springframework.ui.Model;
import th.ac.hotel.model.Booking;
import th.ac.hotel.controller.BookingController.BookingResult;
public final class BookingUI {
    public static void displayBookings(List<Booking> matches,Model model) {model.addAttribute("bookings",matches);}
    public static void displayBookingResult(BookingResult result,Model model) {model.addAttribute("bookingResult",result);model.addAttribute("formErrors",result.validationErrors());}
}
