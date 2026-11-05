package th.ac.hotel.model;

import java.time.LocalDateTime;
import java.util.List;

public record Booking(int bookingId, String bookerName, String phone, int staffId,
                      LocalDateTime createdAt, List<BookingItem> items) {
    public Booking { items = List.copyOf(items); }
    public long bookedCount() { return items.stream().filter(item -> "BOOKED".equals(item.status())).count(); }
    public boolean hasCheckedIn() { return items.stream().anyMatch(item -> "CHECKED_IN".equals(item.status())); }
    public boolean canCancel() { return bookedCount() > 0 && !hasCheckedIn(); }
    public String reference() { return "BK" + String.format("%06d", bookingId); }
    public String statusLabel() {
        if (bookedCount() > 0) return hasCheckedIn() ? "เข้าพักบางห้องแล้ว" : "จองอยู่";
        return hasCheckedIn() ? "เข้าพักแล้ว" : "ยกเลิกแล้ว";
    }
}
