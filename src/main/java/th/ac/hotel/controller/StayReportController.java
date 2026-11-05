package th.ac.hotel.controller;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class StayReportController extends DeskSupport {
    public StayReportController(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock){super(jdbc,manager,clock);}
    private static final String REPORT="""
        SELECT s.stay_id,r.room_number,g.first_name,g.last_name,s.check_in_date,
          s.expected_check_out_date,s.actual_check_in,s.actual_check_out,
          CASE WHEN s.booking_item_id IS NULL THEN 'WALK_IN' ELSE 'RESERVED' END AS arrival_type
        FROM stay s JOIN room r ON r.room_id=s.room_id JOIN guest g ON g.guest_id=s.guest_id
        """;
    @GetMapping("/reports/stays")
    public String report(@RequestParam(defaultValue="EXPECTED_DEPARTURE") String reportType,@RequestParam(defaultValue="") String reportDate,@RequestParam(defaultValue="") String asOf,@RequestParam(defaultValue="false") boolean generate,Model model) {
        model.addAttribute("activePage","stayreport");model.addAttribute("reportType",reportType);
        model.addAttribute("reportDate",reportDate.isBlank()?now().toLocalDate().plusDays(1).toString():reportDate);model.addAttribute("asOf",asOf.isBlank()?now().toString():asOf);
        if(generate) {
            check(List.of("IN_HOUSE","ARRIVAL","EXPECTED_DEPARTURE").contains(reportType),400,"เลือกประเภทรายงานให้ถูกต้อง");
            List<Map<String,Object>> rows;String criteria;
            if("IN_HOUSE".equals(reportType)) {
                LocalDateTime at=parseTime(asOf);check(!at.isAfter(now()),400,"เวลาอ้างอิง In-house ต้องไม่อยู่ในอนาคต");
                rows=jdbc.queryForList(REPORT+" WHERE s.actual_check_in<=? AND (s.actual_check_out IS NULL OR s.actual_check_out>?) ORDER BY r.room_number,s.stay_id",at,at);criteria="ผู้พัก ณ "+at;
            } else {
                LocalDate day=parseDate(reportDate);
                rows="ARRIVAL".equals(reportType)?jdbc.queryForList(REPORT+" WHERE s.actual_check_in>=? AND s.actual_check_in<? ORDER BY s.actual_check_in,r.room_number",day.atStartOfDay(),day.plusDays(1).atStartOfDay()):jdbc.queryForList(REPORT+" WHERE s.status='ACTIVE' AND s.expected_check_out_date=? ORDER BY r.room_number,s.stay_id",day);
                criteria=("ARRIVAL".equals(reportType)?"ผู้เข้าพักจริงวันที่ ":"คาดว่าจะออกวันที่ ")+day;
            }
            model.addAttribute("reportRows",rows);model.addAttribute("criteria",criteria);model.addAttribute("generatedAt",now());model.addAttribute("reportReady",true);
        }return "stay-report";
    }
}
