package th.ac.hotel.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import th.ac.hotel.model.*;
import th.ac.hotel.security.StaffPrincipal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;

@Controller
public class BookingController {
    private static final Logger log = LoggerFactory.getLogger(BookingController.class);
    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    static final String QUERY_READBACK = """
            SELECT b.booking_id,b.booker_name,b.phone,b.staff_id,b.created_at,
                   bi.booking_item_id,bi.room_id,bi.check_in_date,bi.check_out_date,bi.status AS item_status
            FROM booking b LEFT JOIN booking_item bi ON bi.booking_id=b.booking_id
            WHERE b.booking_id=? ORDER BY bi.booking_item_id
            """;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;
    private final String queryUC02_1;

    public BookingController(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactions, ObjectMapper json) throws IOException {
        this.jdbcTemplate = jdbcTemplate;
        this.json = json;
        this.transaction = new TransactionTemplate(transactions);
        // Re-read committed availability AFTER acquiring sorted room locks.
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.queryUC02_1 = new ClassPathResource("sql/queryUC02_1.sql").getContentAsString(StandardCharsets.UTF_8);
    }

    @GetMapping("/bookings")
    public String findBookings(@RequestParam(defaultValue="") String searchText,
                               @RequestParam(defaultValue="false") boolean includeCancelled,
                               Model model, HttpServletResponse response) {
        RoomCollection collection = loadBookingData(model, response);
        model.addAttribute("searchText", searchText);
        model.addAttribute("includeCancelled", includeCancelled);
        if (collection != null) model.addAttribute("bookings", collection.findBookings(searchText).stream()
                .filter(booking -> includeCancelled || booking.bookedCount() > 0 || booking.hasCheckedIn())
                .toList());
        return "bookings";
    }

    @GetMapping("/bookings/new")
    public String newBooking(@RequestParam(defaultValue="") List<String> roomIds,
                             @RequestParam(defaultValue="") String checkInDate,
                             @RequestParam(defaultValue="") String checkOutDate, Model model, HttpServletResponse response) {
        RoomCollection collection = loadBookingData(model, response);
        if (collection == null) return "bookings";
        List<BookingRow> rows = roomIds.stream().filter(id -> !id.isBlank()).map(id -> new BookingRow("", id, checkInDate, checkOutDate)).toList();
        prepareForm(model, "CREATE", null, "", "", rows, Set.of());
        if (rows.isEmpty()) model.addAttribute("formErrors", List.of("เลือกห้องและวันพักจากหน้าตรวจสอบห้องว่างก่อนสร้างรายการจอง"));
        return "booking-form";
    }

    @GetMapping("/bookings/{id}")
    public String viewBooking(@PathVariable int id, Model model, HttpServletResponse response) {
        RoomCollection collection = loadBookingData(model, response);
        if (collection == null) return "bookings";
        Booking booking = requireBooking(collection, id);
        model.addAttribute("booking", booking);
        return "booking-detail";
    }

    @GetMapping("/bookings/{id}/edit")
    public String editBooking(@PathVariable int id, Model model, HttpServletResponse response) {
        RoomCollection collection = loadBookingData(model, response);
        if (collection == null) return "bookings";
        Booking booking = requireBooking(collection, id);
        List<BookingRow> rows = booking.items().stream().filter(item -> "BOOKED".equals(item.status()))
                .map(item -> new BookingRow(String.valueOf(item.bookingItemId()), String.valueOf(item.roomId()), item.checkInDate().toString(), item.checkOutDate().toString())).toList();
        prepareForm(model, "EDIT", id, booking.bookerName(), booking.phone(), rows, Set.of());
        model.addAttribute("booking", booking);
        if (rows.isEmpty()) { response.setStatus(409); model.addAttribute("formErrors", List.of("ไม่มีห้องจองที่ยังแก้ไขได้")); }
        return "booking-form";
    }

    @GetMapping("/bookings/{id}/cancel")
    public String cancelConfirmation(@PathVariable int id, Model model, HttpServletResponse response) {
        RoomCollection collection = loadBookingData(model, response);
        if (collection == null) return "bookings";
        Booking booking = requireBooking(collection, id);
        model.addAttribute("booking", booking);
        return "booking-cancel";
    }

    @PostMapping("/bookings/save")
    public String submitBookingChange(@RequestParam MultiValueMap<String,String> input, @AuthenticationPrincipal StaffPrincipal staff,
                                      Model model, HttpServletResponse response) {
        if (staff == null) throw new AccessDeniedException("Staff account required");
        RoomCollection collection = loadBookingData(model, response);
        if (collection == null) return "bookings";
        String action = value(input, "action");
        Integer id = positiveId(value(input,"bookingId"));
        String name = value(input,"bookerName").strip();
        String phone = value(input,"phone").strip();
        List<BookingRow> rows = postedRows(input);
        Set<String> removedIds = new LinkedHashSet<>(input.getOrDefault("removedItemIds", List.of()));
        prepareForm(model, action, id, name, phone, rows, removedIds);
        if (id != null) model.addAttribute("booking", collection.findBooking(id));
        try {
            BookingChange change = validateRequest(action, id, name, phone, rows, removedIds, input);
            // execute() returns only after the write, read-back, mapping AND COMMIT succeed.
            Booking saved = saveBookingChange(change, staff.employeeId());
            collection.applySavedBooking(saved);
            return "redirect:/bookings/" + saved.bookingId() + "?saved=" + action.toLowerCase(Locale.ROOT);
        } catch (BookingProblem problem) {
            response.setStatus(problem.status);
            model.addAttribute("formErrors", problem.errors);
        } catch (DataAccessException problem) {
            log.warn("UC02 transaction failed: {}", problem.getClass().getSimpleName());
            response.setStatus(503);
            model.addAttribute("formErrors", List.of("บันทึกไม่สำเร็จ ระบบย้อนกลับรายการนี้แล้ว กรุณาโหลดข้อมูลและลองใหม่"));
        }
        return "CANCEL".equals(action) && model.getAttribute("booking") != null ? "booking-cancel" : "booking-form";
    }

    private BookingChange validateRequest(String action, Integer id, String name, String phone, List<BookingRow> rows,
                                          Set<String> removedIds, MultiValueMap<String,String> input) {
        List<String> errors = new ArrayList<>();
        if (!List.of("CREATE","EDIT","CANCEL").contains(action)) errors.add("เลือกประเภทการทำรายการให้ถูกต้อง");
        if (!"CREATE".equals(action) && id == null) errors.add("ไม่พบรหัสการจองที่ต้องการทำรายการ");
        if ("CREATE".equals(action) && id != null) errors.add("การสร้างรายการใหม่ต้องไม่อ้างรหัสการจองเดิม");
        if (!"CANCEL".equals(action)) {
            if (name.isBlank() || name.length()>200) errors.add("กรอกชื่อผู้จองไม่เกิน 200 ตัวอักษร");
            if (phone.isBlank() || phone.length()>30) errors.add("กรอกเบอร์โทรไม่เกิน 30 ตัวอักษร");
            int size = input.getOrDefault("roomIds", List.of()).size();
            if (input.getOrDefault("itemIds",List.of()).size()!=size || input.getOrDefault("checkInDates",List.of()).size()!=size || input.getOrDefault("checkOutDates",List.of()).size()!=size) errors.add("ข้อมูลรายการห้องไม่ครบ กรุณาเปิดแบบฟอร์มใหม่");
        }
        List<ItemChange> items = new ArrayList<>();
        Set<Integer> seenIds = new HashSet<>();
        for (BookingRow row : rows) {
            Integer itemId = row.itemId().isBlank() ? null : positiveId(row.itemId());
            if (!row.itemId().isBlank() && (itemId == null || !seenIds.add(itemId))) errors.add("รหัสรายการห้องไม่ถูกต้องหรือซ้ำ");
            if (removedIds.contains(row.itemId())) continue;
            Integer roomId = positiveId(row.roomId());
            try {
                LocalDate in = LocalDate.parse(row.checkInDate()), out = LocalDate.parse(row.checkOutDate());
                if (roomId == null || !out.isAfter(in)) throw new IllegalArgumentException();
                items.add(new ItemChange(itemId, new RoomRequest(roomId,in,out)));
            } catch (DateTimeParseException | IllegalArgumentException problem) { errors.add("รายการห้องต้องระบุห้องและวันออกหลังวันเริ่มพักอย่างน้อย 1 คืน"); }
        }
        Set<Integer> removed = new LinkedHashSet<>();
        for (String raw : removedIds) {
            Integer itemId = positiveId(raw);
            if (itemId == null) errors.add("รหัสห้องที่ต้องการลดไม่ถูกต้อง"); else removed.add(itemId);
        }
        if ("CREATE".equals(action) && (items.isEmpty() || !removed.isEmpty() || items.stream().anyMatch(item -> item.itemId()!=null))) errors.add("เลือกห้องจาก UC01 อย่างน้อย 1 ห้องสำหรับการจองใหม่");
        if (!errors.isEmpty()) throw new BookingProblem(400, errors);
        return new BookingChange(action,id,name,phone,items,removed);
    }

    private Booking saveBookingChange(BookingChange change, int staffId) {
        return transaction.execute(status -> {
            Booking before = null;
            if (!"CREATE".equals(change.action())) {
                var locked = jdbcTemplate.queryForList("SELECT booking_id FROM booking WHERE booking_id=? FOR UPDATE", change.bookingId());
                if (locked.isEmpty()) throw new BookingProblem(409, List.of("ไม่พบรายการจองแล้ว กรุณาค้นหาใหม่"));
                before = mapToBooking(jdbcTemplate.queryForList(QUERY_READBACK, change.bookingId()));
                checkEligibleItems(change, before);
            }
            SortedSet<Integer> roomIds = new TreeSet<>();
            change.items().forEach(item -> roomIds.add(item.request().roomId()));
            if (before != null) before.items().forEach(item -> roomIds.add(item.roomId()));
            if (roomIds.isEmpty()) throw new BookingProblem(400, List.of("ต้องมีรายการห้องอย่างน้อยหนึ่งห้อง"));
            String placeholders = String.join(",", Collections.nCopies(roomIds.size(), "?"));
            jdbcTemplate.queryForList("SELECT room_id FROM room WHERE room_id IN ("+placeholders+") ORDER BY room_id FOR UPDATE", roomIds.toArray());
            RoomCollection fresh = new RoomCollection();
            fresh.storeBookingData(jdbcTemplate.query(queryUC02_1, bookingContextExtractor));
            if (!"CANCEL".equals(change.action())) {
                var conflicts = fresh.checkAvailability(change.items().stream().map(ItemChange::request).toList(), change.bookingId());
                if (!conflicts.isEmpty()) throw new BookingProblem(409, conflicts.stream().map(conflict -> {
                    Room room = fresh.findRoom(conflict.roomId());
                    return "ห้อง " + (room==null ? conflict.roomId() : room.roomNumber()) + ": " + conflict.reason();
                }).toList());
            }
            LocalDateTime now = LocalDateTime.now(BANGKOK);
            int bookingId;
            if ("CREATE".equals(change.action())) {
                bookingId = insert("INSERT INTO booking(booker_name,phone,staff_id,created_at) VALUES (?,?,?,?)", change.bookerName(),change.phone(),staffId,now);
            } else {
                bookingId = before.bookingId();
                String snapshot;
                try { snapshot = json.writeValueAsString(Map.of("booking",before,"changed_by_staff_id",staffId)); }
                catch (JsonProcessingException e) { throw new IllegalStateException("Cannot build booking history", e); }
                requireOne(jdbcTemplate.update("INSERT INTO booking_history(booking_id,changed_at,action,previous_details) VALUES (?,?,?,?)", bookingId,now,change.action(),snapshot));
                if ("EDIT".equals(change.action())) requireOne(jdbcTemplate.update("UPDATE booking SET booker_name=?,phone=? WHERE booking_id=?",change.bookerName(),change.phone(),bookingId));
            }
            if ("CANCEL".equals(change.action())) {
                int affected = jdbcTemplate.update("UPDATE booking_item SET status='CANCELLED' WHERE booking_id=? AND status='BOOKED'",bookingId);
                if (affected != before.bookedCount()) throw new BookingProblem(409,List.of("รายการห้องเปลี่ยนไป กรุณาโหลดใหม่"));
            } else {
                for (int removed : change.removedIds()) requireOne(jdbcTemplate.update("UPDATE booking_item SET status='CANCELLED' WHERE booking_id=? AND booking_item_id=? AND status='BOOKED'",bookingId,removed));
                for (ItemChange item : change.items()) {
                    var request = item.request();
                    if (item.itemId()==null) insert("INSERT INTO booking_item(booking_id,room_id,check_in_date,check_out_date,status) VALUES (?,?,?,?,'BOOKED')",bookingId,request.roomId(),request.checkInDate(),request.checkOutDate());
                    else requireOne(jdbcTemplate.update("UPDATE booking_item SET room_id=?,check_in_date=?,check_out_date=? WHERE booking_id=? AND booking_item_id=? AND status='BOOKED'",request.roomId(),request.checkInDate(),request.checkOutDate(),bookingId,item.itemId()));
                }
            }
            // queryUC02_3 and mapping are INSIDE the transaction, before commit.
            return mapToBooking(jdbcTemplate.queryForList(QUERY_READBACK, bookingId));
        });
    }

    private void checkEligibleItems(BookingChange change, Booking before) {
        if ("CANCEL".equals(change.action())) {
            if (!before.canCancel()) throw new BookingProblem(409,List.of("ยกเลิกทั้งหมดไม่ได้: มีห้องเช็คอินแล้วหรือรายการถูกยกเลิกไปแล้ว หากต้องลดห้องที่ยังไม่เข้าให้ใช้แก้ไข"));
            return;
        }
        if (before.bookedCount()==0) throw new BookingProblem(409,List.of("ไม่มีรายการห้องที่ยังแก้ไขได้"));
        Set<Integer> expected = new HashSet<>();
        before.items().stream().filter(item -> "BOOKED".equals(item.status())).forEach(item -> expected.add(item.bookingItemId()));
        Set<Integer> supplied = new HashSet<>(change.removedIds());
        for (ItemChange item : change.items()) if (item.itemId()!=null && !supplied.add(item.itemId())) throw new BookingProblem(400,List.of("รายการห้องซ้ำ"));
        if (!expected.equals(supplied)) throw new BookingProblem(409,List.of("รายการห้องเปลี่ยนไปหรือมีรายการที่แก้ไขไม่ได้ กรุณาเปิดแบบฟอร์มใหม่"));
        if (change.items().isEmpty() && !before.hasCheckedIn()) throw new BookingProblem(400,List.of("หากไม่เข้าพักเลย ให้ใช้ยกเลิกการจองทั้งหมด แทนการลดห้องจนหมด"));
    }

    private RoomCollection loadBookingData(Model model, HttpServletResponse response) {
        model.addAttribute("activePage","bookings"); model.addAttribute("bookings",List.of());
        try {
            BookingContext data = jdbcTemplate.query(queryUC02_1, bookingContextExtractor);
            RoomCollection collection = new RoomCollection(); collection.storeBookingData(data);
            model.addAttribute("loadSucceeded",true); model.addAttribute("rooms",collection.rooms());
            Map<Integer,Room> roomById = new LinkedHashMap<>(); collection.rooms().forEach(room -> roomById.put(room.roomId(),room));
            model.addAttribute("roomById",roomById); return collection;
        } catch (DataAccessException problem) {
            log.warn("UC02 load failed: {}",problem.getClass().getSimpleName());
            response.setStatus(503); model.addAttribute("loadSucceeded",false); model.addAttribute("loadError","โหลดข้อมูลการจองไม่สำเร็จ กรุณาตรวจฐานข้อมูลแล้วลองอีกครั้ง"); return null;
        }
    }

    private void prepareForm(Model model,String action,Integer id,String name,String phone,List<BookingRow> rows,Set<String> removed) {
        model.addAttribute("action",action); model.addAttribute("bookingId",id); model.addAttribute("bookerName",name);
        model.addAttribute("phone",phone); model.addAttribute("rows",rows); model.addAttribute("removedItemIds",removed); model.addAttribute("formErrors",List.of());
    }
    private Booking requireBooking(RoomCollection collection,int id) {
        Booking booking=collection.findBooking(id);
        if (booking==null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
        return booking;
    }
    private List<BookingRow> postedRows(MultiValueMap<String,String> input) {
        List<BookingRow> rows=new ArrayList<>(); var ids=input.getOrDefault("roomIds",List.of());
        for(int i=0;i<ids.size();i++) rows.add(new BookingRow(at(input,"itemIds",i),ids.get(i),at(input,"checkInDates",i),at(input,"checkOutDates",i)));
        return rows;
    }
    private String at(MultiValueMap<String,String> input,String key,int i) { var values=input.getOrDefault(key,List.of());return i<values.size()?values.get(i):""; }
    private String value(MultiValueMap<String,String> input,String key) { return Objects.requireNonNullElse(input.getFirst(key),""); }
    private Integer positiveId(String value) { try { int id=Integer.parseInt(value);return id>0?id:null; } catch(NumberFormatException e) { return null; } }
    private void requireOne(int affected) { if(affected!=1) throw new BookingProblem(409,List.of("ข้อมูลเปลี่ยนไประหว่างทำรายการ กรุณาโหลดใหม่")); }
    private int insert(String sql,Object...args) {
        var key=new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> { var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);return statement; },key);
        return Objects.requireNonNull(key.getKey()).intValue();
    }
    private Booking mapToBooking(List<Map<String,Object>> records) {
        if(records.isEmpty()) throw new BookingProblem(409,List.of("อ่านรายการที่บันทึกกลับไม่ได้"));
        var first=records.getFirst(); List<BookingItem> items=new ArrayList<>();
        for(var row:records) if(row.get("booking_item_id")!=null) items.add(new BookingItem(number(row,"booking_item_id"),number(row,"booking_id"),number(row,"room_id"),date(row,"check_in_date"),date(row,"check_out_date"),(String)row.get("item_status")));
        return new Booking(number(first,"booking_id"),(String)first.get("booker_name"),(String)first.get("phone"),number(first,"staff_id"),((Timestamp)first.get("created_at")).toLocalDateTime(),items);
    }
    private int number(Map<String,Object> row,String key) { return ((Number)row.get(key)).intValue(); }
    private LocalDate date(Map<String,Object> row,String key) { return ((Date)row.get(key)).toLocalDate(); }

    private final ResultSetExtractor<BookingContext> bookingContextExtractor = rs -> {
        Map<Integer,Room> rooms=new LinkedHashMap<>(); Map<Integer,Map<Integer,BlockingInterval>> intervals=new LinkedHashMap<>();
        Map<Integer,Booking> bookings=new LinkedHashMap<>(); Map<Integer,Map<Integer,BookingItem>> items=new LinkedHashMap<>();
        while(rs.next()) {
            int roomId=rs.getInt("room_id");
            if(!rooms.containsKey(roomId)) { rooms.put(roomId,new Room(roomId,rs.getString("room_number"),rs.getString("type_name"),rs.getString("bed_type"),rs.getString("occupancy"),rs.getString("cleanliness"),rs.getString("usability"),rs.getString("condition_reason"),rs.getBigDecimal("standard_nightly_rate"),List.of()));intervals.put(roomId,new LinkedHashMap<>()); }
            if(rs.getObject("stay_id")!=null) intervals.get(roomId).putIfAbsent(rs.getInt("stay_id"),new BlockingInterval("STAY",rs.getInt("stay_id"),rs.getDate("stay_check_in").toLocalDate(),rs.getDate("stay_check_out").toLocalDate()));
            if(rs.getObject("booking_id")!=null) {
                int id=rs.getInt("booking_id");
                if(!bookings.containsKey(id)) { bookings.put(id,new Booking(id,rs.getString("booker_name"),rs.getString("phone"),rs.getInt("staff_id"),rs.getTimestamp("created_at").toLocalDateTime(),List.of()));items.put(id,new LinkedHashMap<>()); }
                if(rs.getObject("booking_item_id")!=null) items.get(id).putIfAbsent(rs.getInt("booking_item_id"),new BookingItem(rs.getInt("booking_item_id"),id,roomId,rs.getDate("booked_check_in").toLocalDate(),rs.getDate("booked_check_out").toLocalDate(),rs.getString("item_status")));
            }
        }
        List<Room> groupedRooms=new ArrayList<>(); rooms.forEach((id,r)->groupedRooms.add(new Room(r.roomId(),r.roomNumber(),r.roomType(),r.bedType(),r.occupancy(),r.cleanliness(),r.usability(),r.conditionReason(),r.standardNightlyRate(),new ArrayList<>(intervals.get(id).values()))));
        List<Booking> groupedBookings=new ArrayList<>(); bookings.forEach((id,b)->groupedBookings.add(new Booking(b.bookingId(),b.bookerName(),b.phone(),b.staffId(),b.createdAt(),new ArrayList<>(items.get(id).values()))));
        return new BookingContext(groupedRooms,groupedBookings);
    };

    public record BookingRow(String itemId,String roomId,String checkInDate,String checkOutDate) {}
    private record ItemChange(Integer itemId,RoomRequest request) {}
    private record BookingChange(String action,Integer bookingId,String bookerName,String phone,List<ItemChange> items,Set<Integer> removedIds) {}
    private static class BookingProblem extends RuntimeException {
        final int status; final List<String> errors;
        BookingProblem(int status,List<String> errors) { super(String.join("; ",errors));this.status=status;this.errors=List.copyOf(errors); }
    }
}
