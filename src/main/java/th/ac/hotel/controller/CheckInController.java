package th.ac.hotel.controller;

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
        var collection=new RoomCollection();collection.storeRoomStates(loadRooms());
        model.addAttribute("activePage","checkin");model.addAttribute("rooms",collection.rooms());
        model.addAttribute("guests",jdbc.queryForList("SELECT * FROM guest ORDER BY guest_id DESC"));
        model.addAttribute("recipients",jdbc.queryForList("SELECT * FROM document_recipient ORDER BY recipient_name"));
        var booked=bookingId==null?List.<Map<String,Object>>of():jdbc.queryForList("SELECT bi.*,r.room_number FROM booking_item bi JOIN room r ON r.room_id=bi.room_id WHERE bi.booking_id=? AND bi.status='BOOKED' ORDER BY r.room_number",bookingId);
        model.addAttribute("bookedItems",booked);
        LocalDate night=chargedNight(now());
        model.addAttribute("checkInDate",booked.isEmpty()?night:date(booked.getFirst(),"check_in_date"));model.addAttribute("checkOutDate",booked.isEmpty()?night.plusDays(1):date(booked.getFirst(),"check_out_date"));model.addAttribute("bookingId",bookingId);
        return "check-in";
    }
    @PostMapping("/check-in/save")
    public String save(@RequestParam MultiValueMap<String,String> input,@AuthenticationPrincipal StaffPrincipal staff) {
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
        List<Integer> saved=Objects.requireNonNull(transaction.execute(tx -> {
            // Booking edits/cancellations also lock booking before sorted room locks.
            var bookingIds=new TreeSet<Integer>();
            for(int item:linked.values())bookingIds.add(number(one("SELECT booking_id FROM booking_item WHERE booking_item_id=?",item),"booking_id"));
            for(int booking:bookingIds)one("SELECT booking_id FROM booking WHERE booking_id=? FOR UPDATE",booking);
            for(int room:new TreeSet<>(roomIds))one("SELECT room_id FROM room WHERE room_id=? FOR UPDATE",room);
            one("SELECT guest_id FROM guest WHERE guest_id=?",guest);
            if(recipient!=null)one("SELECT recipient_id FROM document_recipient WHERE recipient_id=?",recipient);
            var collection=new RoomCollection();collection.storeRoomStates(loadRooms());
            List<Integer> stays=new ArrayList<>();
            for(int roomId:roomIds) {
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
                int stay=insert("INSERT INTO stay(room_id,guest_id,staff_id,booking_item_id,default_recipient_id,check_in_date,expected_check_out_date,actual_check_in,key_count,status) VALUES (?,?,?,?,?,?,?,?,?,'ACTIVE')",roomId,guest,staff.employeeId(),item,recipient,in,out,actual,keys);
                for(LocalDate night=in;night.isBefore(out);night=night.plusDays(1))changed(jdbc.update("INSERT INTO stay_charge(stay_id,charge_date,amount) VALUES (?,?,?)",stay,night,room.standardNightlyRate()));
                changed(jdbc.update("UPDATE room SET occupancy='OCCUPIED' WHERE room_id=? AND occupancy='VACANT'",roomId));
                if(item!=null)changed(jdbc.update("UPDATE booking_item SET status='CHECKED_IN' WHERE booking_item_id=? AND status='BOOKED'",item));
                StayAccount loaded=account(stay);check(loaded.charges().size()==ChronoUnit.DAYS.between(in,out),409,"อ่านคืนพักที่บันทึกกลับไม่ครบ");
                stays.add(loaded.id());
            }
            return List.copyOf(stays);
        }));
        return "redirect:/stays/"+saved.getFirst()+"?checkedIn="+saved.size();
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
}
