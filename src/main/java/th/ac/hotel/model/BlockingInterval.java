package th.ac.hotel.model;

import java.time.LocalDate;

public record BlockingInterval(String kind, int id, LocalDate checkIn, LocalDate checkOut) {
    public boolean overlaps(LocalDate requestedIn, LocalDate requestedOut) {
        return checkIn.isBefore(requestedOut) && checkOut.isAfter(requestedIn);
    }
}
