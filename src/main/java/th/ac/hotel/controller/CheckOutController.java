package th.ac.hotel.controller;

import th.ac.hotel.ui.CheckOutUI;

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
public class CheckOutController extends DeskSupport {
    public CheckOutController(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock){super(jdbc,manager,clock);}
    @GetMapping("/check-out")
    public String findCheckOutDetails(@RequestParam(defaultValue="") String searchText,@RequestParam(required=false) Integer stayId,Model model) {
        var result=prepareCheckOutDetails(getActiveStayCheckOutData(searchText));
        model.addAttribute("activePage","checkout");model.addAttribute("searchText",searchText);CheckOutUI.displayCheckOutDetails(result,model);
        if(stayId!=null)model.addAttribute("account",account(stayId));return "check-out";
    }
    private List<StayAccount> getActiveStayCheckOutData(String roomNumberOrStayId) {return mapToCheckOutData(findAccounts(roomNumberOrStayId,true));}
    private List<StayAccount> mapToCheckOutData(List<StayAccount> records) {
        return records.stream().map(record->new StayAccount(record.stay(),List.copyOf(record.charges()),List.copyOf(record.allocations()),List.copyOf(record.receipts()))).toList();
    }
    private CheckOutDetailsResult prepareCheckOutDetails(List<StayAccount> data) {
        return new CheckOutDetailsResult(List.copyOf(data),data.stream().map(StayAccount::balance).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add));
    }
    @PostMapping("/check-out/save")
    public String checkOut(@RequestParam MultiValueMap<String,String> input) {
        int stayId=id(value(input,"stayId"));
        StayAccount selectedStayData=account(stayId);
        try {validateCheckOut(stayId,"true".equals(value(input,"departureConfirmed")),"true".equals(value(input,"mattersResolved")),selectedStayData);}
        catch(DeskProblem problem) {throw makeValidationResult(problem);}
        var savedData=saveCheckOut(stayId,now());
        var collection=new RoomCollection();collection.applySavedCheckOut(savedData);
        CheckOutResult result=makeCheckOutResult(savedData);
        return CheckOutUI.displayCheckOutResult(result);
    }
    private void validateCheckOut(int stayId,boolean departureConfirmed,boolean mattersResolved,StayAccount selectedStayData) {
        check(departureConfirmed&&mattersResolved,400,"ยืนยันว่าแขกออกจริง คืนกุญแจ ตรวจเอกสาร และจัดการเรื่องที่แจ้งไว้แล้วก่อนเช็คเอาต์");
        check(selectedStayData.id()==stayId&&selectedStayData.active(),409,"การพักนี้เช็คเอาต์แล้ว");
        check(selectedStayData.settled(),409,"ยังมีค่าห้องค้างชำระ กรุณารับชำระให้ครบก่อน");
    }
    private CheckOutData saveCheckOut(int stayId,java.time.LocalDateTime checkOutTime) {
        return Objects.requireNonNull(transaction.execute(tx->{
            lockAccounts(List.of(stayId));var selected=account(stayId);
            // Recheck under the same locks used by payments and stay changes.
            validateCheckOut(stayId,true,true,selected);
            changed(jdbc.update("UPDATE stay SET status='CHECKED_OUT',actual_check_out=? WHERE stay_id=? AND status='ACTIVE'",checkOutTime,stayId));
            changed(jdbc.update("UPDATE room SET occupancy='VACANT',cleanliness='DIRTY' WHERE room_id=?",selected.roomId()));
            var saved=account(stayId);
            var room=jdbc.query("SELECT r.*,rt.standard_nightly_rate FROM room r JOIN room_type rt ON rt.type_name=r.type_name WHERE r.room_id=?",(rs,n)->new Room(rs.getInt("room_id"),rs.getString("room_number"),rs.getString("type_name"),rs.getString("bed_type"),rs.getString("occupancy"),rs.getString("cleanliness"),rs.getString("usability"),rs.getString("condition_reason"),rs.getBigDecimal("standard_nightly_rate"),List.of()),saved.roomId()).getFirst();
            return mapToSavedCheckOutData(saved,room);
        }));
    }
    private CheckOutData mapToSavedCheckOutData(StayAccount saved,Room room) {
        check(!saved.active()&&"VACANT".equals(saved.stay().get("occupancy"))&&"DIRTY".equals(saved.stay().get("cleanliness")),409,"อ่านผลเช็คเอาต์กลับไม่สำเร็จ");
        return new CheckOutData(saved,room);
    }
    private DeskProblem makeValidationResult(DeskProblem problem) {return new DeskProblem(problem.status,problem.getMessage());}
    private CheckOutResult makeCheckOutResult(CheckOutData savedData) {return new CheckOutResult(savedData.account().id(),savedData.account().stay().get("actual_check_out"));}
    public record CheckOutResult(int stayId,Object checkOutTime) {}
    public record CheckOutDetailsResult(List<StayAccount> accounts,java.math.BigDecimal balance) {}
}
