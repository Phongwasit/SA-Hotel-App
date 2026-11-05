package th.ac.hotel.controller;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class CashReportController extends DeskSupport {
    public CashReportController(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock){super(jdbc,manager,clock);}
    @GetMapping("/reports/cash")
    public String report(@RequestParam(defaultValue="") String startAt,@RequestParam(defaultValue="") String endAt,@RequestParam(defaultValue="false") boolean generate,Model model) {
        model.addAttribute("activePage","cashreport");model.addAttribute("startAt",startAt.isBlank()?now().toLocalDate().atStartOfDay().toString():startAt);model.addAttribute("endAt",endAt.isBlank()?now().toLocalDate().plusDays(1).atStartOfDay().toString():endAt);
        if(generate) {
            LocalDateTime start=parseTime(startAt),end=parseTime(endAt);check(start.isBefore(end),400,"เวลาเริ่มต้องอยู่ก่อนเวลาสิ้นสุด");
            var payments=jdbc.queryForList("SELECT p.payment_id,p.received_at,p.staff_id,p.evidence,SUM(pm.amount) AS cash_amount FROM payment p JOIN payment_method pm ON pm.payment_id=p.payment_id WHERE pm.method='CASH' AND p.received_at>=? AND p.received_at<? GROUP BY p.payment_id,p.received_at,p.staff_id,p.evidence ORDER BY p.received_at,p.payment_id",start,end);
            List<CashRow> rows=new ArrayList<>();
            for(var p:payments) {
                int id=number(p,"payment_id");
                var rooms=jdbc.queryForList("SELECT DISTINCT s.stay_id,r.room_number,s.check_in_date,s.expected_check_out_date FROM payment_allocation pa JOIN payment_method pm ON pm.payment_method_id=pa.payment_method_id AND pm.payment_id=pa.payment_id JOIN stay s ON s.stay_id=pa.stay_id JOIN room r ON r.room_id=s.room_id WHERE pa.payment_id=? AND pm.method='CASH' ORDER BY r.room_number,s.stay_id",id);
                var refs=jdbc.queryForList("SELECT DISTINCT rr.reference_id,rr.book_number,rr.receipt_number,rr.receipt_date FROM receipt_payment rp JOIN receipt_reference rr ON rr.reference_id=rp.reference_id WHERE rp.payment_id=? AND EXISTS (SELECT 1 FROM payment_allocation pa JOIN payment_method pm ON pm.payment_method_id=pa.payment_method_id AND pm.payment_id=pa.payment_id JOIN receipt_stay rs ON rs.stay_id=pa.stay_id AND rs.reference_id=rp.reference_id WHERE pa.payment_id=rp.payment_id AND pm.method='CASH') ORDER BY rr.reference_id",id);
                rows.add(new CashRow(p,rooms,refs));
            }
            model.addAttribute("reportRows",rows);model.addAttribute("total",payments.stream().map(p->(BigDecimal)p.get("cash_amount")).reduce(BigDecimal.ZERO,BigDecimal::add));model.addAttribute("generatedAt",now());model.addAttribute("reportReady",true);
        }return "cash-report";
    }
    public record CashRow(Map<String,Object> payment,List<Map<String,Object>> rooms,List<Map<String,Object>> receipts) {}
}
