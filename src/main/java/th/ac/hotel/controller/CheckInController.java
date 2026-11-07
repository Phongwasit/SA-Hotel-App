package th.ac.hotel.controller;

import th.ac.hotel.ui.CheckInUI;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import th.ac.hotel.model.*;
import th.ac.hotel.security.StaffPrincipal;

@Controller
public class CheckInController extends DeskSupport {
    public CheckInController(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock){super(jdbc,manager,clock);}
    public static LocalDate chargedNight(LocalDateTime arrival) {return arrival.toLocalTime().isBefore(LocalTime.NOON)?arrival.toLocalDate().minusDays(1):arrival.toLocalDate();}
    @GetMapping("/check-in")
    public String form(@RequestParam(required=false) Integer bookingId,Model model) {
        var context=loadCheckInContext();var collection=new RoomCollection();collection.storeCheckInData(context.rooms().stream().map(Room::roomId).toList(),context);
        model.addAttribute("activePage","checkin");model.addAttribute("rooms",collection.rooms());
        model.addAttribute("guests",jdbc.queryForList("SELECT guest_id,title,first_name,last_name,official_id,CONCAT_WS(' ',title,first_name,last_name) AS guest_name FROM guest ORDER BY guest_id DESC"));
        model.addAttribute("recipients",jdbc.queryForList("SELECT * FROM document_recipient ORDER BY recipient_name"));
        var booked=bookingId==null?List.<Map<String,Object>>of():jdbc.queryForList("SELECT bi.*,r.room_number FROM booking_item bi JOIN room r ON r.room_id=bi.room_id WHERE bi.booking_id=? AND bi.status='BOOKED' ORDER BY r.room_number",bookingId);
        model.addAttribute("bookedItems",booked);
        LocalDate night=chargedNight(now());
        model.addAttribute("checkInDate",booked.isEmpty()?night:date(booked.getFirst(),"check_in_date"));model.addAttribute("checkOutDate",booked.isEmpty()?night.plusDays(1):date(booked.getFirst(),"check_out_date"));model.addAttribute("bookingId",bookingId);
        return "check-in";
    }
    @PostMapping("/check-in/save")
    public String checkIn(@RequestParam MultiValueMap<String,String> input,@AuthenticationPrincipal StaffPrincipal staff) {
        CheckInContext context=loadCheckInContext();
        var collection=new RoomCollection();collection.storeCheckInData(context.rooms().stream().map(Room::roomId).toList(),context);
        StayDraft parsed;
        try {parsed=validateCheckIn(input,staff.employeeId());}
        catch(DeskProblem problem) {throw makeValidationResult(problem,List.of());}
        var issues=collection.checkCheckInReadiness(parsed.roomStays(),parsed.guestId(),parsed.nightlyRates(),parsed.staffId(),parsed.keyCount(),new ArrayList<>(parsed.linked().values()));
        if(!issues.isEmpty())throw makeValidationResult(null,issues);
        StayDraft stayDraft=buildStayDraft(parsed,collection);
        CheckInData saved=saveCheckIn(stayDraft,new ArrayList<>(stayDraft.linked().values()));
        collection.applySavedCheckIn(saved);
        CheckInResult result=makeCheckInResult(saved);
        return CheckInUI.displayCheckInResult(result);
    }
    @GetMapping("/stays")
    public String stays(@RequestParam(defaultValue="") String searchText,Model model) {
        model.addAttribute("activePage","stays");model.addAttribute("searchText",searchText);model.addAttribute("accounts",findAccounts(searchText,false));return "stays";
    }
    @GetMapping("/stays/{id}")
    public String stay(@PathVariable int id,Model model) {
        model.addAttribute("activePage","stays");model.addAttribute("account",account(id));
        model.addAttribute("recipients",jdbc.queryForList("SELECT * FROM document_recipient ORDER BY recipient_name"));return "stay-detail";
    }
    @PostMapping("/stays/{id}/recipient")
    public String setRecipient(@PathVariable int id,@RequestParam(defaultValue="") String recipientId) {
        Integer recipient=optionalId(recipientId);
        transaction.execute(tx -> {
            lockAccounts(List.of(id));check(account(id).active(),409,"แก้ผู้รับเริ่มต้นได้ขณะยังพักอยู่เท่านั้น");
            if(recipient!=null)one("SELECT recipient_id FROM document_recipient WHERE recipient_id=?",recipient);
            changed(jdbc.update("UPDATE stay SET default_recipient_id=? WHERE stay_id=? AND status='ACTIVE'",recipient,id));account(id);return null;
        });return "redirect:/stays/"+id+"?saved=true";
    }
    private List<Room> loadRooms() {
        return jdbc.query("SELECT r.*,rt.standard_nightly_rate FROM room r JOIN room_type rt ON rt.type_name=r.type_name ORDER BY r.room_number",(rs,n)->new Room(rs.getInt("room_id"),rs.getString("room_number"),rs.getString("type_name"),rs.getString("bed_type"),rs.getString("occupancy"),rs.getString("cleanliness"),rs.getString("usability"),rs.getString("condition_reason"),rs.getBigDecimal("standard_nightly_rate"),List.of()));
    }

    private StayDraft validateCheckIn(MultiValueMap<String,String> input,int staffId) {

        int guest=id(value(input,"guestId"));Integer recipient=optionalId(value(input,"recipientId"));
        int keys=id(value(input,"keyCount"));check(keys<=2,400,"จำนวนกุญแจต่อห้องต้องเป็น 1 หรือ 2");
        List<Integer> roomIds=ids(input.getOrDefault("roomIds",List.of()));
        List<String> postedItems=input.getOrDefault("bookingItemIds",List.of());
        check(postedItems.isEmpty()||postedItems.size()==roomIds.size(),400,"รายการจองกับห้องไม่ตรงกัน");
        LocalDate in=parseDate(value(input,"checkInDate")),out=parseDate(value(input,"checkOutDate"));
        LocalDateTime actual=now();
        check(in.equals(chargedNight(actual)),400,"คืนเริ่มพักต้องตรงกับวันที่เข้าจริง: ก่อนเที่ยงใช้คืนก่อนหน้า หลังเที่ยงใช้คืนวันนี้");
        check(out.isAfter(in),400,"วันออกต้องอยู่หลังวันเริ่มพัก");
        check(in.getYear()>=1000&&out.getYear()<=9999,400,"วันพักอยู่นอกช่วงวันที่ที่ฐานข้อมูลรองรับ");
        var linked=new HashMap<Integer,Integer>();
        for(int i=0;i<postedItems.size();i++) {Integer item=optionalId(postedItems.get(i));if(item!=null)linked.put(roomIds.get(i),item);}
        if(postedItems.isEmpty())for(int room:roomIds){Integer item=optionalId(value(input,"bookingItem_"+room));if(item!=null)linked.put(room,item);}
        check(new HashSet<>(linked.values()).size()==linked.size(),400,"รายการจองซ้ำ");

        return new StayDraft(roomIds.stream().map(room->new RoomRequest(room,in,out)).toList(),guest,recipient,keys,staffId,actual,Map.copyOf(linked),Map.of());
    }
    private StayDraft buildStayDraft(StayDraft input,RoomCollection collection) {
        Map<Integer,java.math.BigDecimal> nightlyRates=new LinkedHashMap<>();
        input.roomStays().forEach(request->nightlyRates.put(request.roomId(),Objects.requireNonNull(collection.findRoom(request.roomId())).standardNightlyRate()));
        return new StayDraft(input.roomStays(),input.guestId(),input.recipientId(),input.keyCount(),input.staffId(),input.actualCheckIn(),input.linked(),Map.copyOf(nightlyRates));
    }
    private CheckInData saveCheckIn(StayDraft stayDraft,List<Integer> selectedBookingItemIds) {
        int guest=stayDraft.guestId(),keys=stayDraft.keyCount();Integer recipient=stayDraft.recipientId();
        var roomIds=stayDraft.roomStays().stream().map(RoomRequest::roomId).toList();var linked=stayDraft.linked();
        LocalDate in=stayDraft.roomStays().getFirst().checkInDate(),out=stayDraft.roomStays().getFirst().checkOutDate();LocalDateTime actual=stayDraft.actualCheckIn();
        return Objects.requireNonNull(transaction.execute(tx->{

            // Booking edits/cancellations also lock booking before sorted room locks.
            var bookingIds=new TreeSet<Integer>();
            for(int item:linked.values())bookingIds.add(number(one("SELECT booking_id FROM booking_item WHERE booking_item_id=?",item),"booking_id"));
            for(int booking:bookingIds)one("SELECT booking_id FROM booking WHERE booking_id=? FOR UPDATE",booking);
            for(int room:new TreeSet<>(roomIds))one("SELECT room_id FROM room WHERE room_id=? FOR UPDATE",room);
            one("SELECT guest_id FROM guest WHERE guest_id=?",guest);
            if(recipient!=null)one("SELECT recipient_id FROM document_recipient WHERE recipient_id=?",recipient);
            var collection=new RoomCollection();
            CheckInContext context=loadCheckInContext();collection.storeCheckInData(roomIds,context);
            var issues=collection.checkCheckInReadiness(stayDraft.roomStays(),guest,stayDraft.nightlyRates(),stayDraft.staffId(),keys,new ArrayList<>(linked.values()));
            check(issues.isEmpty(),409,issues.isEmpty()?"":issues.getFirst().reason());
            TransactionCommands commands=buildCheckInCommands(stayDraft);
            if(!linked.isEmpty())addBookingCheckInCommands(commands,new ArrayList<>(linked.values()));
            List<Integer> stays=new ArrayList<>();
            for(RoomRequest request:commands.roomStays()) {
                int roomId=request.roomId();
                Room room=collection.findRoom(roomId);
                check(room!=null && "VACANT".equals(room.occupancy())&&"CLEAN".equals(room.cleanliness())&&"USABLE".equals(room.usability()),409,"ห้องไม่พร้อมเช็คอิน ต้องว่าง สะอาด และใช้งานได้");
                Integer item=linked.get(roomId);
                if(item!=null) {
                    var booking=one("SELECT * FROM booking_item WHERE booking_item_id=? FOR UPDATE",item);
                    check("BOOKED".equals(booking.get("status")) && number(booking,"room_id")==roomId && date(booking,"check_in_date").equals(in)&&date(booking,"check_out_date").equals(out),409,"รายการจองไม่ตรงกับห้องหรือคืนพัก หรือเช็คอินแล้ว");
                }
                int bookings=jdbc.queryForObject("SELECT COUNT(*) FROM booking_item WHERE room_id=? AND status='BOOKED' AND check_in_date<? AND check_out_date>? AND (? IS NULL OR booking_item_id<>?)",Integer.class,roomId,out,in,item,item);
                int active=jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE room_id=? AND status='ACTIVE'",Integer.class,roomId);
                check(bookings==0&&active==0,409,"ห้องมีการจองอื่นทับวัน หรือยังมีผู้พักอยู่");
                int stay=insert("INSERT INTO stay(room_id,guest_id,staff_id,booking_item_id,default_recipient_id,check_in_date,expected_check_out_date,actual_check_in,key_count,status) VALUES (?,?,?,?,?,?,?,?,?,'ACTIVE')",roomId,guest,stayDraft.staffId(),item,recipient,in,out,actual,keys);
                for(LocalDate night=in;night.isBefore(out);night=night.plusDays(1))changed(jdbc.update("INSERT INTO stay_charge(stay_id,charge_date,amount) VALUES (?,?,?)",stay,night,room.standardNightlyRate()));
                changed(jdbc.update("UPDATE room SET occupancy='OCCUPIED' WHERE room_id=? AND occupancy='VACANT'",roomId));
                if(item!=null&&commands.bookingItemIds().contains(item))changed(jdbc.update("UPDATE booking_item SET status='CHECKED_IN' WHERE booking_item_id=? AND status='BOOKED'",item));
                StayAccount loaded=account(stay);check(loaded.charges().size()==ChronoUnit.DAYS.between(in,out),409,"อ่านคืนพักที่บันทึกกลับไม่ครบ");
                stays.add(loaded.id());
            }
            return mapToCheckInData(stays,roomIds,new ArrayList<>(linked.values()),in,out);

        }));
    }
    private TransactionCommands buildCheckInCommands(StayDraft stayDraft) {
        return new TransactionCommands(List.copyOf(stayDraft.roomStays()),new ArrayList<>());
    }
    private void addBookingCheckInCommands(TransactionCommands commands,List<Integer> selectedBookingItemIds) {
        commands.bookingItemIds().addAll(selectedBookingItemIds);
    }
    private CheckInData mapToCheckInData(List<Integer> stayIds,List<Integer> roomIds,List<Integer> selectedBookingItemIds,LocalDate in,LocalDate out) {
        var accounts=stayIds.stream().map(this::account).toList();
        accounts.forEach(loaded->check(loaded.charges().size()==ChronoUnit.DAYS.between(in,out),409,"อ่านคืนพักที่บันทึกกลับไม่ครบ"));
        return new CheckInData(accounts,loadRooms().stream().filter(room->roomIds.contains(room.roomId())).toList(),selectedBookingItemIds);
    }
    private DeskProblem makeValidationResult(DeskProblem validationError,List<RoomConflict> issues) {
        return validationError!=null?new DeskProblem(validationError.status,validationError.getMessage()):new DeskProblem(409,issues.getFirst().reason());
    }
    private CheckInResult makeCheckInResult(CheckInData savedData) {return new CheckInResult(savedData.stays().stream().map(StayAccount::id).toList());}
    public record CheckInResult(List<Integer> stayIds) {}
    private record StayDraft(List<RoomRequest> roomStays,int guestId,Integer recipientId,int keyCount,int staffId,LocalDateTime actualCheckIn,Map<Integer,Integer> linked,Map<Integer,java.math.BigDecimal> nightlyRates) {}
    private record TransactionCommands(List<RoomRequest> roomStays,List<Integer> bookingItemIds) {}
    private CheckInContext loadCheckInContext() {
        // queryUC04_1: one fresh joined context per load; map before replacing the collection.
        return jdbc.query("""
            SELECT r.room_id,r.room_number,r.type_name,r.bed_type,r.occupancy,r.cleanliness,r.usability,r.condition_reason,rt.standard_nightly_rate,
              b.block_kind,b.block_id,b.booking_id,b.check_in_date,b.check_out_date,b.item_status
            FROM room r JOIN room_type rt ON rt.type_name=r.type_name
            LEFT JOIN (
              SELECT 'BOOKING' AS block_kind,booking_item_id AS block_id,booking_id,room_id,check_in_date,check_out_date,status AS item_status FROM booking_item
              UNION ALL
              SELECT 'STAY',stay_id,NULL,room_id,check_in_date,expected_check_out_date,status FROM stay WHERE status='ACTIVE'
            ) b ON b.room_id=r.room_id ORDER BY r.room_number,b.block_kind,b.block_id
            """,checkInContextExtractor);
    }
    private final org.springframework.jdbc.core.ResultSetExtractor<CheckInContext> checkInContextExtractor=rs->{
        Map<Integer,Room> rooms=new LinkedHashMap<>();Map<Integer,BookingItem> items=new LinkedHashMap<>();Set<Integer> active=new HashSet<>();
        while(rs.next()) {
            int id=rs.getInt("room_id");
            if(!rooms.containsKey(id))rooms.put(id,new Room(id,rs.getString("room_number"),rs.getString("type_name"),rs.getString("bed_type"),rs.getString("occupancy"),rs.getString("cleanliness"),rs.getString("usability"),rs.getString("condition_reason"),rs.getBigDecimal("standard_nightly_rate"),List.of()));
            if("BOOKING".equals(rs.getString("block_kind")))items.put(rs.getInt("block_id"),new BookingItem(rs.getInt("block_id"),rs.getInt("booking_id"),id,rs.getDate("check_in_date").toLocalDate(),rs.getDate("check_out_date").toLocalDate(),rs.getString("item_status")));
            if("STAY".equals(rs.getString("block_kind")))active.add(id);
        }
        return new CheckInContext(new ArrayList<>(rooms.values()),items,active);
    };

}
