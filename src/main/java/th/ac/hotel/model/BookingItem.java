package th.ac.hotel.model;

import java.time.LocalDate;

public record BookingItem(int bookingItemId, int bookingId, int roomId, LocalDate checkInDate,
                          LocalDate checkOutDate, String status) {
    public boolean overlaps(LocalDate in, LocalDate out) {
        return checkInDate.isBefore(out) && checkOutDate.isAfter(in);
    }
    public String statusLabel() {
        return switch (status) { case "BOOKED" -> "จองอยู่"; case "CHECKED_IN" -> "เช็คอินแล้ว"; default -> "ยกเลิกแล้ว"; };
    }
}
