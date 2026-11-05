package th.ac.hotel.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import th.ac.hotel.model.StayAccount;

@Controller
public class DocumentController extends DeskSupport {
    public DocumentController(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock){super(jdbc,manager,clock);}
    @GetMapping("/documents")
    public String documents(@RequestParam MultiValueMap<String,String> input,Model model) {
        model.addAttribute("activePage","documents");model.addAttribute("accounts",findAccounts("",false));
        model.addAttribute("recipients",jdbc.queryForList("SELECT * FROM document_recipient ORDER BY recipient_name"));
        model.addAttribute("references",jdbc.queryForList("SELECT rr.*,dr.recipient_name FROM receipt_reference rr JOIN document_recipient dr ON dr.recipient_id=rr.recipient_id ORDER BY reference_id DESC"));
        model.addAttribute("today",now().toLocalDate());
        if(!input.getOrDefault("stayIds",List.of()).isEmpty()||!value(input,"referenceId").isEmpty()) {
            DocumentView view=transaction.execute(tx->load(input,false));
            model.addAttribute("document",view);model.addAttribute("mode","REFERENCE".equals(value(input,"mode"))?"REFERENCE":"FOLIO");model.addAttribute("generatedAt",now());
        }
        return "documents";
    }
    @PostMapping("/documents/save")
    public String save(@RequestParam MultiValueMap<String,String> input) {
        String book=text(input,"bookNumber",30,true),receipt=text(input,"receiptNumber",30,true);
        var receiptDate=parseDate(value(input,"receiptDate"));check(!receiptDate.isAfter(now().toLocalDate()),400,"วันที่ใบเสร็จต้องไม่อยู่ในอนาคต");
        int reference=Objects.requireNonNull(transaction.execute(tx -> {
            DocumentView view=load(input,true);
            var requested=input.getOrDefault("paymentIds",List.of());
            check(new HashSet<>(ids(requested)).equals(new HashSet<>(view.paymentIds())),400,"เงินรับที่เชื่อมต้องครบและสัมพันธ์กับคืนพักที่เลือก กรุณาโหลดข้อมูลใหม่");
            int id;
            if(view.referenceId()==null) id=insert("INSERT INTO receipt_reference(recipient_id,book_number,receipt_number,receipt_date) VALUES (?,?,?,?)",view.recipientId(),book,receipt,receiptDate);
            else {
                id=view.referenceId();one("SELECT reference_id FROM receipt_reference WHERE reference_id=? FOR UPDATE",id);
                changed(jdbc.update("UPDATE receipt_reference SET recipient_id=?,book_number=?,receipt_number=?,receipt_date=? WHERE reference_id=?",view.recipientId(),book,receipt,receiptDate,id));
            }
            // Only synchronize this reference's links. Stays/payments and other receipts remain intact.
            var oldStays=jdbc.queryForList("SELECT stay_id FROM receipt_stay WHERE reference_id=?",Integer.class,id);
            for(int old:oldStays)if(!view.stayIds().contains(old))jdbc.update("DELETE FROM receipt_stay WHERE reference_id=? AND stay_id=?",id,old);
            for(int stay:view.stayIds())if(!oldStays.contains(stay))changed(jdbc.update("INSERT INTO receipt_stay(reference_id,stay_id) VALUES (?,?)",id,stay));
            var oldPayments=jdbc.queryForList("SELECT payment_id FROM receipt_payment WHERE reference_id=?",Integer.class,id);
            for(int old:oldPayments)if(!view.paymentIds().contains(old))jdbc.update("DELETE FROM receipt_payment WHERE reference_id=? AND payment_id=?",id,old);
            for(int payment:view.paymentIds())if(!oldPayments.contains(payment))changed(jdbc.update("INSERT INTO receipt_payment(reference_id,payment_id) VALUES (?,?)",id,payment));
            var saved=one("SELECT * FROM receipt_reference WHERE reference_id=?",id);
            check(number(saved,"recipient_id")==view.recipientId(),409,"อ่านผู้รับใบเสร็จกลับไม่สำเร็จ");
            check(jdbc.queryForList("SELECT stay_id FROM receipt_stay WHERE reference_id=?",Integer.class,id).size()==view.stayIds().size(),409,"อ่านรายการพักที่เชื่อมกลับไม่ครบ");
            check(jdbc.queryForList("SELECT payment_id FROM receipt_payment WHERE reference_id=?",Integer.class,id).size()==view.paymentIds().size(),409,"อ่านเงินรับที่เชื่อมกลับไม่ครบ");
            return id;
        }));return "redirect:/documents?referenceId="+reference+"&mode=REFERENCE&saved=true";
    }
    private DocumentView load(MultiValueMap<String,String> input,boolean saving) {
        Integer reference=optionalId(value(input,"referenceId"));
        Map<String,Object> existing=reference==null?Map.of():one("SELECT * FROM receipt_reference WHERE reference_id=?",reference);
        List<String> selected=input.getOrDefault("stayIds",List.of());
        List<Integer> stays=selected.isEmpty()&&reference!=null?jdbc.queryForList("SELECT stay_id FROM receipt_stay WHERE reference_id=? ORDER BY stay_id",Integer.class,reference):ids(selected);
        check(!stays.isEmpty(),400,"เลือกการเข้าพักที่ต้องการออกเอกสาร");
        lockAccounts(stays);
        var accounts=stays.stream().map(this::account).toList();
        for(var a:accounts) {
            check(a.settled(),409,"การพัก #"+a.id()+" ยังชำระค่าห้องไม่ครบ จัดการรับเงินก่อนออกเอกสาร");
            if(saving)check(a.receipts().stream().noneMatch(r->reference==null||number(r,"reference_id")!=reference),409,"การพักนี้มีใบเสร็จสุดท้ายแล้ว ให้เปิดแก้ไขใบเดิม");
        }
        Integer recipient=optionalId(value(input,"recipientId"));String source="เลือกผู้รับสำหรับเอกสารนี้";
        if(recipient==null&&reference!=null) {recipient=number(existing,"recipient_id");source="ผู้รับที่บันทึกกับใบเสร็จเดิม";}
        if(recipient==null) {
            Set<Object> defaults=new HashSet<>();accounts.forEach(a->defaults.add(a.stay().get("default_recipient_id")));
            check(defaults.size()==1&&!defaults.contains(null),400,"ผู้รับเริ่มต้นของแต่ละห้องไม่ตรงกันหรือยังว่าง กรุณาเลือกผู้รับเอกสารหนึ่งราย");
            recipient=((Number)defaults.iterator().next()).intValue();source="ผู้รับเริ่มต้นเดียวกันของทุกห้องที่เลือก";
        }
        var profile=one("SELECT * FROM document_recipient WHERE recipient_id=? FOR UPDATE",recipient);
        check(profile.get("address")!=null&&!profile.get("address").toString().isBlank(),400,"เติมที่อยู่ผู้รับเอกสารใน UC03 ก่อนออกเอกสาร");
        if("ORGANIZATION".equals(profile.get("recipient_type")))check(profile.get("tax_id")!=null&&!profile.get("tax_id").toString().isBlank(),400,"เติมเลขผู้เสียภาษีของหน่วยงานก่อนออกเอกสาร");
        List<Integer> paymentIds=accounts.stream().flatMap(a->a.allocations().stream()).map(a->number(a,"payment_id")).distinct().sorted().toList();
        List<Map<String,Object>> payments=new ArrayList<>();
        for(int id:paymentIds) {
            var payment=new HashMap<>(one("SELECT * FROM payment WHERE payment_id=?",id));
            payment.put("methods",jdbc.queryForList("SELECT method,amount FROM payment_method WHERE payment_id=? ORDER BY payment_method_id",id));
            payment.put("allocated_amount",accounts.stream().flatMap(a->a.allocations().stream()).filter(a->number(a,"payment_id")==id).map(a->(BigDecimal)a.get("amount")).reduce(BigDecimal.ZERO,BigDecimal::add));payments.add(payment);
        }
        return new DocumentView(reference,recipient,profile,source,accounts,stays,paymentIds,payments,existing,accounts.stream().map(StayAccount::total).reduce(BigDecimal.ZERO,BigDecimal::add));
    }
    public record DocumentView(Integer referenceId,int recipientId,Map<String,Object> recipient,String recipientSource,List<StayAccount> accounts,List<Integer> stayIds,List<Integer> paymentIds,List<Map<String,Object>> payments,Map<String,Object> reference,BigDecimal total) {}
}
