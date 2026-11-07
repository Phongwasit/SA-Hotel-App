package th.ac.hotel.controller;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import th.ac.hotel.model.*;

@Controller
public class CashReportController extends DeskSupport {
    public CashReportController(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock){super(jdbc,manager,clock);}
    private static final String queryUC10_1="SELECT p.payment_id,p.received_at,p.staff_id,p.evidence,SUM(pm.amount) AS cash_amount FROM payment p JOIN payment_method pm ON pm.payment_id=p.payment_id WHERE pm.method='CASH' AND p.received_at>=? AND p.received_at<? GROUP BY p.payment_id,p.received_at,p.staff_id,p.evidence ORDER BY p.received_at,p.payment_id";
    @GetMapping("/reports/cash")
    public String cashReportPage(@RequestParam(defaultValue="") String startAt,@RequestParam(defaultValue="") String endAt,@RequestParam(defaultValue="false") boolean generate,Model model) {
        model.addAttribute("activePage","cashreport");model.addAttribute("startAt",startAt.isBlank()?now().toLocalDate().atStartOfDay().toString():startAt);model.addAttribute("endAt",endAt.isBlank()?now().toLocalDate().plusDays(1).atStartOfDay().toString():endAt);
        if(generate) {
            var result=generateCashReport(parseTime(startAt),parseTime(endAt));
            if(!result.previewReady())throw new DeskProblem(result.status(),result.error()!=null?result.error():String.join(" ",result.validationErrors()));
            th.ac.hotel.ui.CashReportUI.displayReportResult(result,model);
        }return "cash-report";
    }
    public ReportResult<CashRow> generateCashReport(LocalDateTime startAt,LocalDateTime endAt) {
        var errors=validateCriteria(startAt,endAt);
        if(!errors.isEmpty())return makeValidationResult(errors);
        ReadResult readResult=loadCashReportData(startAt,endAt);
        if(readResult.error()!=null)return makeLoadFailureResult(readResult.error());
        var rows=mapToCashReportRows(readResult.cashRows(),readResult.stayRows(),readResult.receiptRows());
        return buildCashReport(new ReportCriteria(null,null,null,startAt,endAt),rows,now());
    }
    private List<String> validateCriteria(LocalDateTime startAt,LocalDateTime endAt) {
        return startAt==null||endAt==null?List.of("ระบุวันเวลาให้ถูกต้อง"):!startAt.isBefore(endAt)?List.of("เวลาเริ่มต้องอยู่ก่อนเวลาสิ้นสุด"):List.of();
    }
    private ReadResult loadCashReportData(LocalDateTime startAt,LocalDateTime endAt) {
        try {
            var cashRows=jdbc.queryForList(queryUC10_1,startAt,endAt);
            var paymentIds=cashRows.stream().map(row->number(row,"payment_id")).distinct().toList();
            if(paymentIds.isEmpty())return new ReadResult(cashRows,List.of(),List.of(),null);
            String placeholders=String.join(",",Collections.nCopies(paymentIds.size(),"?"));
            // queryUC10_2/3: expand the selected IDs, bind each parameter; retain CASH-only detail rules.
            var stayRows=jdbc.queryForList("SELECT DISTINCT pa.payment_id,s.stay_id,r.room_number,s.check_in_date,s.expected_check_out_date FROM payment_allocation pa JOIN payment_method pm ON pm.payment_method_id=pa.payment_method_id AND pm.payment_id=pa.payment_id JOIN stay s ON s.stay_id=pa.stay_id JOIN room r ON r.room_id=s.room_id WHERE pa.payment_id IN ("+placeholders+") AND pm.method='CASH' ORDER BY pa.payment_id,r.room_number,s.stay_id",paymentIds.toArray());
            var receiptRows=jdbc.queryForList("SELECT DISTINCT rp.payment_id,rr.reference_id,rr.book_number,rr.receipt_number,rr.receipt_date FROM receipt_payment rp JOIN receipt_reference rr ON rr.reference_id=rp.reference_id WHERE rp.payment_id IN ("+placeholders+") AND EXISTS (SELECT 1 FROM payment_allocation pa JOIN payment_method pm ON pm.payment_method_id=pa.payment_method_id AND pm.payment_id=pa.payment_id JOIN receipt_stay rs ON rs.stay_id=pa.stay_id AND rs.reference_id=rp.reference_id WHERE pa.payment_id=rp.payment_id AND pm.method='CASH') ORDER BY rp.payment_id,rr.reference_id",paymentIds.toArray());
            return new ReadResult(cashRows,stayRows,receiptRows,null);
        } catch(org.springframework.dao.DataAccessException error) {return new ReadResult(List.of(),List.of(),List.of(),"โหลดข้อมูลรายงานไม่สำเร็จ กรุณาตรวจฐานข้อมูลแล้วลองอีกครั้ง");}
    }
    private List<CashRow> mapToCashReportRows(List<Map<String,Object>> cashRows,List<Map<String,Object>> stayRows,List<Map<String,Object>> receiptRows) {
        return cashRows.stream().map(payment->{
            int id=number(payment,"payment_id");
            return new CashRow(Collections.unmodifiableMap(new LinkedHashMap<>(payment)),stayRows.stream().filter(row->number(row,"payment_id")==id).toList(),receiptRows.stream().filter(row->number(row,"payment_id")==id).toList());
        }).toList();
    }
    private ReportResult<CashRow> buildCashReport(ReportCriteria criteria,List<CashRow> rows,LocalDateTime generatedAt) {
        BigDecimal total=rows.stream().map(row->(BigDecimal)row.payment().get("cash_amount")).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new ReportResult<>(new ReportPreview<>(criteria,generatedAt,rows,total),List.of(),null,200);
    }
    private ReportResult<CashRow> makeValidationResult(List<String> validationErrors) {return new ReportResult<>(null,List.copyOf(validationErrors),null,400);}
    private ReportResult<CashRow> makeLoadFailureResult(String error) {return new ReportResult<>(null,List.of(),error,503);}
    private record ReadResult(List<Map<String,Object>> cashRows,List<Map<String,Object>> stayRows,List<Map<String,Object>> receiptRows,String error) {}
    public record CashRow(Map<String,Object> payment,List<Map<String,Object>> rooms,List<Map<String,Object>> receipts) {}
}
