package th.ac.hotel.model;

import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

/** A single load/search cycle's collection; never static, cached, or a singleton bean. */
public class RoomCollection {
    private List<Room> rooms = List.of();
    private final Map<Integer, Booking> bookings = new LinkedHashMap<>();

    public void storeRoomList(List<Room> availableRooms) {
        rooms = List.copyOf(availableRooms);
        bookings.clear();
    }

    public List<Room> getRooms(String roomType, LocalDate checkInDate, LocalDate checkOutDate, int requiredRooms) {
        return rooms.stream()
                .filter(room -> room.roomType().equals(roomType) && room.usability().equals("USABLE"))
                .filter(room -> room.blockingIntervals().stream().noneMatch(interval -> interval.overlaps(checkInDate, checkOutDate)))
                .filter(room -> bookings.values().stream().flatMap(booking -> booking.items().stream()).noneMatch(item -> item.roomId()==room.roomId() && "BOOKED".equals(item.status()) && item.overlaps(checkInDate, checkOutDate)))
                .toList();
    }

    public int size() { return rooms.size(); }

    public void storeBookingData(BookingContext data) {
        storeRoomList(data.rooms());
        bookings.clear();
        data.bookings().forEach(booking -> bookings.put(booking.bookingId(), booking));
    }

    public List<Booking> findBookings(String searchText) {
        String term = searchText == null ? "" : searchText.strip().toLowerCase(Locale.ROOT);
        return bookings.values().stream().filter(booking -> term.isEmpty()
                || String.valueOf(booking.bookingId()).equals(term)
                || booking.reference().toLowerCase(Locale.ROOT).contains(term)
                || booking.bookerName().toLowerCase(Locale.ROOT).contains(term)
                || booking.phone().contains(term)).toList().reversed();
    }

    public Booking findBooking(int id) { return bookings.get(id); }
    public Room findRoom(int id) { return rooms.stream().filter(room -> room.roomId() == id).findFirst().orElse(null); }
    public List<Room> rooms() { return rooms; }

    public void storeRoomStates(List<Room> states) {
        Map<Integer,Room> merged=new LinkedHashMap<>();
        rooms.forEach(room -> merged.put(room.roomId(),room));
        for(Room state:states) {
            Room old=merged.get(state.roomId());
            merged.put(state.roomId(),new Room(state.roomId(),state.roomNumber(),state.roomType(),state.bedType(),
                    state.occupancy(),state.cleanliness(),state.usability(),state.conditionReason(),state.standardNightlyRate(),
                    old==null?state.blockingIntervals():old.blockingIntervals()));
        }
        rooms=List.copyOf(merged.values());
    }

    public List<RoomConflict> checkAvailability(List<RoomRequest> requests, Integer excludedBookingId) {
        List<RoomConflict> conflicts = new ArrayList<>();
        for (int i=0; i<requests.size(); i++) {
            RoomRequest request = requests.get(i);
            Room room = findRoom(request.roomId());
            String reason = null;
            if (room == null || !"USABLE".equals(room.usability())) reason = "ห้องไม่พร้อมใช้งาน";
            else if (room.blockingIntervals().stream().anyMatch(interval -> interval.overlaps(request.checkInDate(), request.checkOutDate()))) reason = "ทับช่วงเข้าพักที่ยังไม่สิ้นสุด";
            else {
                boolean booked = bookings.values().stream()
                        .filter(booking -> excludedBookingId == null || booking.bookingId() != excludedBookingId)
                        .flatMap(booking -> booking.items().stream())
                        .anyMatch(item -> item.roomId() == request.roomId() && "BOOKED".equals(item.status()) && item.overlaps(request.checkInDate(), request.checkOutDate()));
                if (booked) reason = "มีรายการจองทับช่วงวันที่เลือก";
            }
            for (int j=0; j<i && reason==null; j++) {
                RoomRequest other = requests.get(j);
                if (other.roomId() == request.roomId() && other.checkInDate().isBefore(request.checkOutDate()) && other.checkOutDate().isAfter(request.checkInDate())) reason = "เลือกห้องซ้ำในช่วงคืนพักที่ทับกัน";
            }
            if (reason != null) conflicts.add(new RoomConflict(request.roomId(), request.checkInDate(), request.checkOutDate(), reason));
        }
        return List.copyOf(conflicts);
    }

    public void applySavedBooking(Booking savedBooking) { bookings.put(savedBooking.bookingId(), savedBooking); }
}
