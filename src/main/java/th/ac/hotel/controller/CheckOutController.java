package th.ac.hotel.controller;

import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@Controller
public class CheckOutController extends DeskSupport {
    public CheckOutController(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock){super(jdbc,manager,clock);}
    @GetMapping("/check-out")
    public String form(@RequestParam(defaultValue="") String searchText,@RequestParam(required=false) Integer stayId,Model model) {
        model.addAttribute("activePage","checkout");model.addAttribute("searchText",searchText);model.addAttribute("accounts",findAccounts(searchText,true));
        if(stayId!=null)model.addAttribute("account",account(stayId));return "check-out";
    }
    @PostMapping("/check-out/save")
    public String save(@RequestParam MultiValueMap<String,String> input) {
        int id=id(value(input,"stayId"));
        check("true".equals(value(input,"departureConfirmed"))&&"true".equals(value(input,"mattersResolved")),400,"ยืนยันว่าแขกออกจริง คืนกุญแจ ตรวจเอกสาร และจัดการเรื่องที่แจ้งไว้แล้วก่อนเช็คเอาต์");
        transaction.execute(tx -> {
            lockAccounts(List.of(id));var account=account(id);
            check(account.active(),409,"การพักนี้เช็คเอาต์แล้ว");check(account.settled(),409,"ยังมีค่าห้องค้างชำระ กรุณารับชำระให้ครบก่อน");
            changed(jdbc.update("UPDATE stay SET status='CHECKED_OUT',actual_check_out=? WHERE stay_id=? AND status='ACTIVE'",now(),id));
            changed(jdbc.update("UPDATE room SET occupancy='VACANT',cleanliness='DIRTY' WHERE room_id=?",account.roomId()));
            var saved=account(id);check(!saved.active()&&"VACANT".equals(saved.stay().get("occupancy"))&&"DIRTY".equals(saved.stay().get("cleanliness")),409,"อ่านผลเช็คเอาต์กลับไม่สำเร็จ");return saved;
        });return "redirect:/stays/"+id+"?checkedOut=true";
    }
}
