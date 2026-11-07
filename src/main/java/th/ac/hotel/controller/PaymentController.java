package th.ac.hotel.controller;

import th.ac.hotel.ui.PaymentUI;

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
    public String findStayBalances(@RequestParam(defaultValue="") String searchText,Model model) {
        model.addAttribute("activePage","payments");model.addAttribute("searchText",searchText);BalanceResult result=calculateBalances(findStayPaymentData(searchText));PaymentUI.displayStayBalances(result,model);model.addAttribute("receivedAt",now());return "payments";
    }
    @PostMapping("/payments/save")
    public String recordPayment(@RequestParam MultiValueMap<String,String> input,@AuthenticationPrincipal StaffPrincipal staff) {
        PaymentDraft draft;
        try {draft=validatePayment(input,staff.employeeId());}
        catch(DeskProblem problem) {throw makeValidationResult(problem);}
        try {
            SavedPaymentData savedData=savePayment(draft);
            BalanceResult balances=calculateBalances(savedData.stayPaymentData());
            PaymentResult result=makeSuccessResult(savedData.payment(),balances);
            return PaymentUI.displayPaymentResult(result);
        } catch(org.springframework.dao.DataAccessException problem) {
            PaymentResult result=makeSaveFailureResult(problem);
            throw new DeskProblem(result.status(),result.error());
        }
    }
    @GetMapping("/payments/{id}")
    public String payment(@PathVariable int id,Model model) {
        model.addAttribute("activePage","payments");model.addAttribute("payment",one("SELECT p.*,e.first_name,e.last_name FROM payment p JOIN employee e ON e.employee_id=p.staff_id WHERE p.payment_id=?",id));
        var methods=jdbc.queryForList("SELECT * FROM payment_method WHERE payment_id=? ORDER BY payment_method_id",id);
        model.addAttribute("methods",methods);model.addAttribute("total",methods.stream().map(r->(BigDecimal)r.get("amount")).reduce(BigDecimal.ZERO,BigDecimal::add));
        model.addAttribute("allocations",jdbc.queryForList("SELECT pa.*,sc.charge_date,r.room_number,pm.method FROM payment_allocation pa JOIN stay_charge sc ON sc.charge_id=pa.charge_id JOIN stay s ON s.stay_id=pa.stay_id JOIN room r ON r.room_id=s.room_id JOIN payment_method pm ON pm.payment_method_id=pa.payment_method_id WHERE pa.payment_id=? ORDER BY r.room_number,sc.charge_date",id));return "payment-detail";
    }

    private List<StayPaymentData> findStayPaymentData(String searchText) {return mapToStayPaymentData(findAccounts(searchText,true));}
    private List<StayPaymentData> mapToStayPaymentData(List<StayAccount> records) {
        return records.stream().map(account->new StayPaymentData(account,account.total(),account.paid(),account.balance())).toList();
    }
    private BalanceResult calculateBalances(List<StayPaymentData> data) {
        return new BalanceResult(data.stream().map(StayPaymentData::account).toList(),data.stream().map(StayPaymentData::balance).reduce(BigDecimal.ZERO,BigDecimal::add));
    }
    private PaymentDraft validatePayment(MultiValueMap<String,String> input,int staffId) {

        List<Integer> charges=ids(input.getOrDefault("chargeIds",List.of()));
        var methods=new HashMap<Integer,String>();
        for(int charge:charges) {
            String method=value(input,"method_"+charge);
            check(List.of("CASH","TRANSFER","CARD").contains(method),400,"เลือกวิธีชำระของแต่ละคืน");methods.put(charge,method);
        }
        LocalDateTime received=parseTime(value(input,"receivedAt"));
        check(!received.isAfter(now()),400,"เวลารับเงินต้องไม่อยู่ในอนาคต");
        String evidence=text(input,"evidence",2000,false);

        return new PaymentDraft(charges,Map.copyOf(methods),staffId,received,evidence);
    }
    private SavedPaymentData savePayment(PaymentDraft draft) {
        var charges=draft.chargeIds();var methods=draft.methods();var received=draft.receivedAt();String evidence=draft.evidence();
        return Objects.requireNonNull(transaction.execute(tx->{

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
            int id=insert("INSERT INTO payment(staff_id,received_at,evidence) VALUES (?,?,?)",draft.staffId(),received,nullIfBlank(evidence));
            for(var method:totals.entrySet()) {
                int methodId=insert("INSERT INTO payment_method(payment_id,method,amount) VALUES (?,?,?)",id,method.getKey(),method.getValue());
                for(var row:rows.entrySet())if(methods.get(row.getKey()).equals(method.getKey()))insert("INSERT INTO payment_allocation(payment_id,stay_id,amount,charge_id,payment_method_id) VALUES (?,?,?,?,?)",id,number(row.getValue(),"stay_id"),row.getValue().get("amount"),row.getKey(),methodId);
            }
            return mapToSavedPaymentData(id,stays,totals.size());

        }));
    }
    private SavedPaymentData mapToSavedPaymentData(int paymentId,List<Integer> stayIds,int expectedMethods) {
        var payment=one("SELECT * FROM payment WHERE payment_id=?",paymentId);
        var methods=jdbc.queryForList("SELECT * FROM payment_method WHERE payment_id=?",paymentId);
        check(methods.size()==expectedMethods,409,"อ่านวิธีชำระที่บันทึกกลับไม่ครบ");
        return new SavedPaymentData(payment,mapToStayPaymentData(stayIds.stream().map(this::account).toList()));
    }
    private DeskProblem makeValidationResult(DeskProblem problem) {return new DeskProblem(problem.status,problem.getMessage());}
    private PaymentResult makeSuccessResult(Map<String,Object> payment,BalanceResult balances) {return new PaymentResult(number(payment,"payment_id"),balances,null,200);}
    private PaymentResult makeSaveFailureResult(org.springframework.dao.DataAccessException error) {
        int status=error instanceof org.springframework.dao.DataIntegrityViolationException?409:503;
        return new PaymentResult(null,null,"โหลดหรือบันทึกข้อมูลไม่สำเร็จ รายการที่กำลังบันทึกถูกย้อนกลับ กรุณาตรวจฐานข้อมูลแล้วลองอีกครั้ง",status);
    }
    private record PaymentDraft(List<Integer> chargeIds,Map<Integer,String> methods,int staffId,LocalDateTime receivedAt,String evidence) {}
    public record StayPaymentData(StayAccount account,BigDecimal total,BigDecimal paid,BigDecimal balance) {}
    public record BalanceResult(List<StayAccount> accounts,BigDecimal balance) {}
    private record SavedPaymentData(Map<String,Object> payment,List<StayPaymentData> stayPaymentData) {}
    public record PaymentResult(Integer paymentId,BalanceResult balances,String error,int status) {}

}
