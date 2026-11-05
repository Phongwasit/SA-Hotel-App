package th.ac.hotel.controller;

import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import th.ac.hotel.model.*;

@Controller
public class RoomStatusController extends DeskSupport {
    public RoomStatusController(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock){super(jdbc,manager,clock);}
    @GetMapping("/room-status")
    public String status(@RequestParam(required=false) Integer roomId,Model model) {
        var collection=new RoomCollection();collection.storeRoomStates(loadRooms());
        model.addAttribute("activePage","status");model.addAttribute("rooms",collection.rooms());
        if(roomId!=null) {Room room=collection.findRoom(roomId);check(room!=null,404,"ไม่พบห้อง");model.addAttribute("selectedRoom",room);}
        return "room-status";
    }
    @PostMapping("/room-status/save")
    public String save(@RequestParam MultiValueMap<String,String> input) {
        int room=id(value(input,"roomId"));String clean=value(input,"cleanliness"),usable=value(input,"usability"),reason=text(input,"reason",500,true);
        check(List.of("CLEAN","DIRTY").contains(clean)&&List.of("USABLE","OUT_OF_ORDER").contains(usable),400,"เลือกสถานะความสะอาดและการใช้งานให้ถูกต้อง");
        Room saved=transaction.execute(tx->{
            one("SELECT room_id FROM room WHERE room_id=? FOR UPDATE",room);
            changed(jdbc.update("UPDATE room SET cleanliness=?,usability=?,condition_reason=? WHERE room_id=?",clean,usable,reason,room));
            return loadRooms().stream().filter(r->r.roomId()==room).findFirst().orElseThrow(()->new DeskProblem(409,"อ่านห้องที่บันทึกกลับไม่ได้"));
        });
        var collection=new RoomCollection();collection.storeRoomStates(List.of(Objects.requireNonNull(saved)));
        return "redirect:/room-status?roomId="+room+"&saved=true";
    }
    private List<Room> loadRooms() {
        return jdbc.query("SELECT r.*,rt.standard_nightly_rate FROM room r JOIN room_type rt ON rt.type_name=r.type_name ORDER BY r.room_number",(rs,n)->new Room(rs.getInt("room_id"),rs.getString("room_number"),rs.getString("type_name"),rs.getString("bed_type"),rs.getString("occupancy"),rs.getString("cleanliness"),rs.getString("usability"),rs.getString("condition_reason"),rs.getBigDecimal("standard_nightly_rate"),List.of()));
    }
}
