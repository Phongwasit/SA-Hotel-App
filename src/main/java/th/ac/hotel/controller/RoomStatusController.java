package th.ac.hotel.controller;

import th.ac.hotel.ui.RoomStatusUI;

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
    public String getRoomStatus(@RequestParam(required=false) Integer roomId,Model model) {
        var collection=new RoomCollection();collection.storeRoomStates(loadRooms());
        model.addAttribute("activePage","status");model.addAttribute("rooms",collection.rooms());
        if(roomId!=null) {RoomStatusResult result=getRoomStatus(roomId,collection);RoomStatusUI.displayRoomStatus(result,model);}
        return "room-status";
    }
    @GetMapping(value="/room-status/data",produces="application/json")
    @ResponseBody
    public List<RoomStateView> roomStates() {
        var collection=new RoomCollection();collection.storeRoomStates(loadRooms());
        return collection.rooms().stream().map(room->new RoomStateView(room.roomId(),room.roomNumber(),room.roomType(),
                room.occupancy(),room.cleanliness(),room.usability(),room.conditionReason())).toList();
    }
    public record RoomStateView(int roomId,String roomNumber,String roomType,String occupancy,
                                String cleanliness,String usability,String conditionReason) {}
    @PostMapping("/room-status/save")
    public String updateRoomStatus(@RequestParam MultiValueMap<String,String> input) {
        RoomCondition change;
        try {change=validateStatusChange(input);}
        catch(DeskProblem problem) {throw makeValidationResult(problem);}
        Room saved=saveRoomCondition(change.roomId(),change.cleanliness(),change.usability(),change.reason());
        var collection=new RoomCollection();collection.applySavedRoom(saved);
        RoomStatusResult result=makeRoomStatusSaveResult(saved,"VACANT".equals(saved.occupancy())?evaluateReadiness(saved):null);
        return RoomStatusUI.displayRoomStatusResult(result);
    }
    private List<Room> loadRooms() {
        return jdbc.query("SELECT r.*,rt.standard_nightly_rate FROM room r JOIN room_type rt ON rt.type_name=r.type_name ORDER BY r.room_number",(rs,n)->new Room(rs.getInt("room_id"),rs.getString("room_number"),rs.getString("type_name"),rs.getString("bed_type"),rs.getString("occupancy"),rs.getString("cleanliness"),rs.getString("usability"),rs.getString("condition_reason"),rs.getBigDecimal("standard_nightly_rate"),List.of()));
    }

    private RoomStatusResult getRoomStatus(int roomId,RoomCollection collection) {
        Room room=collection.findRoom(roomId);check(room!=null,404,"ไม่พบห้อง");
        ReadinessResult readiness="VACANT".equals(room.occupancy())?evaluateReadiness(room):null;
        return makeRoomStatusResult(room,readiness);
    }
    private ReadinessResult evaluateReadiness(Room room) {
        boolean ready="VACANT".equals(room.occupancy())&&"CLEAN".equals(room.cleanliness())&&"USABLE".equals(room.usability());
        return new ReadinessResult(ready,room.occupancy(),room.cleanliness(),room.usability());
    }
    private RoomStatusResult makeRoomStatusResult(Room room,ReadinessResult optionalReadiness) {return new RoomStatusResult(room,optionalReadiness,false);}
    private RoomCondition validateStatusChange(MultiValueMap<String,String> input) {

        int room=id(value(input,"roomId"));String clean=value(input,"cleanliness"),usable=value(input,"usability"),reason=text(input,"reason",500,true);
        check(List.of("CLEAN","DIRTY").contains(clean)&&List.of("USABLE","OUT_OF_ORDER").contains(usable),400,"เลือกสถานะความสะอาดและการใช้งานให้ถูกต้อง");

        return new RoomCondition(room,clean,usable,reason);
    }
    private Room saveRoomCondition(int room,String clean,String usable,String reason) {
        return Objects.requireNonNull(transaction.execute(tx->{

            one("SELECT room_id FROM room WHERE room_id=? FOR UPDATE",room);
            changed(jdbc.update("UPDATE room SET cleanliness=?,usability=?,condition_reason=? WHERE room_id=?",clean,usable,reason,room));
            return mapToRoom(loadRooms(),room);

        }));
    }
    private Room mapToRoom(List<Room> savedRows,int roomId) {return savedRows.stream().filter(room->room.roomId()==roomId).findFirst().orElseThrow(()->new DeskProblem(409,"อ่านห้องที่บันทึกกลับไม่ได้"));}
    private DeskProblem makeValidationResult(DeskProblem problem) {return new DeskProblem(problem.status,problem.getMessage());}
    private RoomStatusResult makeRoomStatusSaveResult(Room savedRoom,ReadinessResult optionalReadiness) {return new RoomStatusResult(savedRoom,optionalReadiness,true);}
    private record RoomCondition(int roomId,String cleanliness,String usability,String reason) {}
    public record ReadinessResult(boolean ready,String occupancy,String cleanliness,String usability) {}
    public record RoomStatusResult(Room room,ReadinessResult readiness,boolean saved) {}

}
