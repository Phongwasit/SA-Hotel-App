package th.ac.hotel.controller;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import th.ac.hotel.model.*;

@Controller
public class StayReportController extends DeskSupport {
    public StayReportController(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock){super(jdbc,manager,clock);}
    private static final String REPORT="""
        SELECT s.stay_id,r.room_number,g.title,g.first_name,g.last_name,CONCAT_WS(' ',g.title,g.first_name,g.last_name) AS guest_name,s.check_in_date,
          s.expected_check_out_date,s.actual_check_in,s.actual_check_out,
          CASE WHEN s.booking_item_id IS NULL THEN 'WALK_IN' ELSE 'RESERVED' END AS arrival_type
        FROM stay s JOIN room r ON r.room_id=s.room_id JOIN guest g ON g.guest_id=s.guest_id
        """;
    @GetMapping("/reports/stays")
    public String stayReportPage(@RequestParam(defaultValue="EXPECTED_DEPARTURE") String reportType,@RequestParam(defaultValue="") String reportDate,@RequestParam(defaultValue="") String asOf,@RequestParam(defaultValue="false") boolean generate,Model model) {
        model.addAttribute("activePage","stayreport");model.addAttribute("reportType",reportType);
        model.addAttribute("reportDate",reportDate.isBlank()?now().toLocalDate().plusDays(1).toString():reportDate);model.addAttribute("asOf",asOf.isBlank()?now().toString():asOf);
        if(generate) {
            var result=generateStayReport(reportType,"IN_HOUSE".equals(reportType)?null:parseDate(reportDate),"IN_HOUSE".equals(reportType)?parseTime(asOf):null);
            if(!result.previewReady())throw new DeskProblem(result.status(),result.error()!=null?result.error():String.join(" ",result.validationErrors()));
            th.ac.hotel.ui.StayReportUI.displayReportResult(result,model);
        }return "stay-report";
    }
    public ReportResult<Map<String,Object>> generateStayReport(String reportType,LocalDate reportDate,LocalDateTime asOf) {
        var errors=validateCriteria(reportType,reportDate,asOf);
        if(!errors.isEmpty())return makeValidationResult(errors);
        var criteria=new ReportCriteria(reportType,reportDate,asOf,null,null);
        ReadResult readResult=loadStayReportData(reportType,criteria);
        if(readResult.error()!=null)return makeLoadFailureResult(readResult.error());
        return buildStayReport(reportType,criteria,mapToStayReportRows(readResult.records()),now());
    }
    private List<String> validateCriteria(String reportType,LocalDate reportDate,LocalDateTime asOf) {
        List<String> errors=new ArrayList<>();
        if(!List.of("IN_HOUSE","ARRIVAL","EXPECTED_DEPARTURE").contains(reportType))errors.add("เลือกประเภทรายงานให้ถูกต้อง");
        else if("IN_HOUSE".equals(reportType)) {
            if(asOf==null)errors.add("ระบุวันเวลาให้ถูกต้อง");else if(asOf.isAfter(now()))errors.add("เวลาอ้างอิง In-house ต้องไม่อยู่ในอนาคต");
        } else if(reportDate==null)errors.add("ระบุวันที่ให้ถูกต้อง");
        return List.copyOf(errors);
    }
    private ReadResult loadStayReportData(String reportType,ReportCriteria criteria) {
        try {
            List<Map<String,Object>> records;
            if("IN_HOUSE".equals(reportType))records=jdbc.queryForList(REPORT+" WHERE s.actual_check_in<=? AND (s.actual_check_out IS NULL OR s.actual_check_out>?) ORDER BY r.room_number,s.stay_id",criteria.asOf(),criteria.asOf());
            else if("ARRIVAL".equals(reportType))records=jdbc.queryForList(REPORT+" WHERE s.actual_check_in>=? AND s.actual_check_in<? ORDER BY s.actual_check_in,r.room_number",criteria.reportDate().atStartOfDay(),criteria.reportDate().plusDays(1).atStartOfDay());
            else records=jdbc.queryForList(REPORT+" WHERE s.status='ACTIVE' AND s.expected_check_out_date=? ORDER BY r.room_number,s.stay_id",criteria.reportDate());
            return new ReadResult(records,null);
        } catch(org.springframework.dao.DataAccessException error) {return new ReadResult(List.of(),"โหลดข้อมูลรายงานไม่สำเร็จ กรุณาตรวจฐานข้อมูลแล้วลองอีกครั้ง");}
    }
    private List<Map<String,Object>> mapToStayReportRows(List<Map<String,Object>> records) {return records.stream().map(row->Collections.unmodifiableMap(new LinkedHashMap<>(row))).toList();}
    private ReportResult<Map<String,Object>> buildStayReport(String reportType,ReportCriteria criteria,List<Map<String,Object>> rows,LocalDateTime generatedAt) {
        return new ReportResult<>(new ReportPreview<>(criteria,generatedAt,rows,java.math.BigDecimal.valueOf(rows.size())),List.of(),null,200);
    }
    private ReportResult<Map<String,Object>> makeValidationResult(List<String> validationErrors) {return new ReportResult<>(null,List.copyOf(validationErrors),null,400);}
    private ReportResult<Map<String,Object>> makeLoadFailureResult(String error) {return new ReportResult<>(null,List.of(),error,503);}
    private record ReadResult(List<Map<String,Object>> records,String error) {}
}
