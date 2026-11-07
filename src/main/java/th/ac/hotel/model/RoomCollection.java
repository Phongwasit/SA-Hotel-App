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
    private final Map<Integer,BookingItem> checkInItems=new LinkedHashMap<>();
    private final java.util.Set<Integer> activeRoomIds=new java.util.HashSet<>();
    private final Map<Integer, Booking> bookings = new LinkedHashMap<>();

    public void storeRoomList(List<Room> availableRooms) {
        rooms = List.copyOf(availableRooms);
        bookings.clear();
        checkInItems.clear();
        activeRoomIds.clear();
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

    /** Replace only the freshly loaded selected-room context; preserve unrelated objects. */
    public void storeCheckInData(List<Integer> roomIds,CheckInContext context) {
        java.util.Set<Integer> selected=new java.util.HashSet<>(roomIds);
        Map<Integer,Room> merged=new LinkedHashMap<>();
        rooms.stream().filter(room->!selected.contains(room.roomId())).forEach(room->merged.put(room.roomId(),room));
        context.rooms().stream().filter(room->selected.contains(room.roomId())).forEach(room->merged.put(room.roomId(),room));
        rooms=List.copyOf(merged.values());
        checkInItems.values().removeIf(item->selected.contains(item.roomId()));
        context.bookingItems().forEach((id,item)->{if(selected.contains(item.roomId()))checkInItems.put(id,item);});
        activeRoomIds.removeAll(selected);
        context.activeRoomIds().stream().filter(selected::contains).forEach(activeRoomIds::add);
    }
    public List<RoomConflict> checkCheckInReadiness(List<RoomRequest> roomStays,Integer guestId,
            Map<Integer,java.math.BigDecimal> nightlyRates,Integer staffId,int keyCount,List<Integer> selectedBookingItemIds) {
        List<RoomConflict> issues=new ArrayList<>();
        for(RoomRequest request:roomStays) {
            Room room=findRoom(request.roomId());String reason=null;
            if(room==null||!"VACANT".equals(room.occupancy())||!"CLEAN".equals(room.cleanliness())||!"USABLE".equals(room.usability()))reason="ห้องไม่พร้อมเช็คอิน ต้องว่าง สะอาด และใช้งานได้";
            else if(activeRoomIds.contains(room.roomId()))reason="ห้องมีการจองอื่นทับวัน หรือยังมีผู้พักอยู่";
            else {
                for(int id:selectedBookingItemIds) {
                    BookingItem item=checkInItems.get(id);
                    if(item==null){reason="รายการจองไม่ตรงกับห้องหรือคืนพัก หรือเช็คอินแล้ว";break;}
                    if(item.roomId()==room.roomId()&&(!"BOOKED".equals(item.status())||!item.checkInDate().equals(request.checkInDate())||!item.checkOutDate().equals(request.checkOutDate()))) {reason="รายการจองไม่ตรงกับห้องหรือคืนพัก หรือเช็คอินแล้ว";break;}
                }
                if(reason==null&&checkInItems.values().stream().anyMatch(item->item.roomId()==room.roomId()&&"BOOKED".equals(item.status())&&!selectedBookingItemIds.contains(item.bookingItemId())&&item.overlaps(request.checkInDate(),request.checkOutDate())))reason="ห้องมีการจองอื่นทับวัน หรือยังมีผู้พักอยู่";
            }
            if(reason!=null)issues.add(new RoomConflict(request.roomId(),request.checkInDate(),request.checkOutDate(),reason));
        }
        return List.copyOf(issues);
    }
    /** Called only after saveCheckIn has committed. */
    public void applySavedCheckIn(CheckInData savedData) {
        storeRoomStates(savedData.rooms());
        savedData.stays().forEach(stay->activeRoomIds.add(stay.roomId()));
        rooms=rooms.stream().map(room->{
            List<BlockingInterval> intervals=new ArrayList<>(room.blockingIntervals().stream()
                    .filter(interval->!("BOOKING".equals(interval.kind())&&savedData.selectedBookingItemIds().contains(interval.id()))).toList());
            savedData.stays().stream().filter(stay->stay.roomId()==room.roomId()).forEach(stay->{
                intervals.removeIf(interval->"STAY".equals(interval.kind())&&interval.id()==stay.id());
                intervals.add(new BlockingInterval("STAY",stay.id(),((java.sql.Date)stay.stay().get("check_in_date")).toLocalDate(),((java.sql.Date)stay.stay().get("expected_check_out_date")).toLocalDate()));
            });
            return new Room(room.roomId(),room.roomNumber(),room.roomType(),room.bedType(),room.occupancy(),room.cleanliness(),room.usability(),room.conditionReason(),room.standardNightlyRate(),intervals);
        }).toList();
        savedData.selectedBookingItemIds().forEach(id->checkInItems.computeIfPresent(id,(key,item)->new BookingItem(item.bookingItemId(),item.bookingId(),item.roomId(),item.checkInDate(),item.checkOutDate(),"CHECKED_IN")));
        bookings.replaceAll((id,booking)->new Booking(booking.bookingId(),booking.bookerName(),booking.phone(),booking.staffId(),booking.createdAt(),booking.items().stream().map(item->savedData.selectedBookingItemIds().contains(item.bookingItemId())?new BookingItem(item.bookingItemId(),item.bookingId(),item.roomId(),item.checkInDate(),item.checkOutDate(),"CHECKED_IN"):item).toList()));
    }
    /** Called only after saveCheckOut has committed; preserve other stays and room conditions. */
    public void applySavedCheckOut(CheckOutData savedData) {
        Room old=findRoom(savedData.room().roomId());
        List<BlockingInterval> intervals=old==null?List.of():old.blockingIntervals().stream().filter(interval->!("STAY".equals(interval.kind())&&interval.id()==savedData.account().id())).toList();
        Room room=savedData.room();
        rooms=new ArrayList<>(rooms.stream().filter(value->value.roomId()!=room.roomId()).toList());
        rooms.add(new Room(room.roomId(),room.roomNumber(),room.roomType(),room.bedType(),room.occupancy(),room.cleanliness(),room.usability(),room.conditionReason(),room.standardNightlyRate(),intervals));
        rooms=List.copyOf(rooms);activeRoomIds.remove(room.roomId());
    }
    public void applySavedRoom(Room savedRoom) {storeRoomStates(List.of(savedRoom));}

}
