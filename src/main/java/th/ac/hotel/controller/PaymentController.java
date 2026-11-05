package th.ac.hotel.controller;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import th.ac.hotel.model.StayAccount;
import th.ac.hotel.security.StaffPrincipal;

@Controller
public class PaymentController extends DeskSupport {
    public PaymentController(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock){super(jdbc,manager,clock);}
    @GetMapping("/payments")
    public String payments(@RequestParam(defaultValue="") String searchText,Model model) {
        model.addAttribute("activePage","payments");model.addAttribute("searchText",searchText);model.addAttribute("accounts",findAccounts(searchText,true));model.addAttribute("receivedAt",now());return "payments";
    }
    @PostMapping("/payments/save")
    public String save(@RequestParam MultiValueMap<String,String> input,@AuthenticationPrincipal StaffPrincipal staff) {
        List<Integer> charges=ids(input.getOrDefault("chargeIds",List.of()));
        var methods=new HashMap<Integer,String>();
        for(int charge:charges) {
            String method=value(input,"method_"+charge);
            check(List.of("CASH","TRANSFER","CARD").contains(method),400,"เลือกวิธีชำระของแต่ละคืน");methods.put(charge,method);
        }
        LocalDateTime received=parseTime(value(input,"receivedAt"));
        check(!received.isAfter(now()),400,"เวลารับเงินต้องไม่อยู่ในอนาคต");
        String evidence=text(input,"evidence",2000,false);
        int payment=Objects.requireNonNull(transaction.execute(tx -> {
            List<Integer> stays=charges.stream().map(id -> number(one("SELECT stay_id FROM stay_charge WHERE charge_id=?",id),"stay_id")).distinct().sorted().toList();
            lockAccounts(stays);
            Map<Integer,Map<String,Object>> rows=new LinkedHashMap<>();
            Map<String,BigDecimal> totals=new LinkedHashMap<>();
            for(int charge:new TreeSet<>(charges)) {
                var row=one("SELECT sc.*,s.status,s.actual_check_in FROM stay_charge sc JOIN stay s ON s.stay_id=sc.stay_id WHERE sc.charge_id=? FOR UPDATE",charge);
                check("ACTIVE".equals(row.get("status")),409,"รับเงินได้เฉพาะการพักที่ยังไม่เช็คเอาต์");
                check(!received.isBefore(((java.sql.Timestamp)row.get("actual_check_in")).toLocalDateTime()),400,"เวลารับเงินต้องไม่ก่อนเวลาเช็คอินจริง");
                check(jdbc.queryForObject("SELECT COUNT(*) FROM payment_allocation WHERE charge_id=?",Integer.class,charge)==0,409,"คืนที่เลือกถูกชำระแล้ว กรุณาโหลดใหม่");
                rows.put(charge,row);totals.merge(methods.get(charge),(BigDecimal)row.get("amount"),BigDecimal::add);
            }
            int id=insert("INSERT INTO payment(staff_id,received_at,evidence) VALUES (?,?,?)",staff.employeeId(),received,nullIfBlank(evidence));
            for(var method:totals.entrySet()) {
                int methodId=insert("INSERT INTO payment_method(payment_id,method,amount) VALUES (?,?,?)",id,method.getKey(),method.getValue());
                for(var row:rows.entrySet())if(methods.get(row.getKey()).equals(method.getKey()))insert("INSERT INTO payment_allocation(payment_id,stay_id,amount,charge_id,payment_method_id) VALUES (?,?,?,?,?)",id,number(row.getValue(),"stay_id"),row.getValue().get("amount"),row.getKey(),methodId);
            }
            one("SELECT * FROM payment WHERE payment_id=?",id);
            var savedMethods=jdbc.queryForList("SELECT * FROM payment_method WHERE payment_id=?",id);
            check(savedMethods.size()==totals.size(),409,"อ่านวิธีชำระที่บันทึกกลับไม่ครบ");
            for(int stay:stays)account(stay);
            return id;
        }));
        return "redirect:/payments/"+payment;
    }
    @GetMapping("/payments/{id}")
    public String payment(@PathVariable int id,Model model) {
        model.addAttribute("activePage","payments");model.addAttribute("payment",one("SELECT p.*,e.first_name,e.last_name FROM payment p JOIN employee e ON e.employee_id=p.staff_id WHERE p.payment_id=?",id));
        var methods=jdbc.queryForList("SELECT * FROM payment_method WHERE payment_id=? ORDER BY payment_method_id",id);
        model.addAttribute("methods",methods);model.addAttribute("total",methods.stream().map(r->(BigDecimal)r.get("amount")).reduce(BigDecimal.ZERO,BigDecimal::add));
        model.addAttribute("allocations",jdbc.queryForList("SELECT pa.*,sc.charge_date,r.room_number,pm.method FROM payment_allocation pa JOIN stay_charge sc ON sc.charge_id=pa.charge_id JOIN stay s ON s.stay_id=pa.stay_id JOIN room r ON r.room_id=s.room_id JOIN payment_method pm ON pm.payment_method_id=pa.payment_method_id WHERE pa.payment_id=? ORDER BY r.room_number,sc.charge_date",id));return "payment-detail";
    }
}
