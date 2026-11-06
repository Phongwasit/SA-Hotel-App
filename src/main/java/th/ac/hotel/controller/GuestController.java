package th.ac.hotel.controller;

import java.time.Clock;
import java.util.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import th.ac.hotel.model.NameRules;

@Controller
public class GuestController extends DeskSupport {
    public GuestController(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock) {super(jdbc,manager,clock);}
    @GetMapping("/profiles")
    public String profiles(@RequestParam(defaultValue="GUEST") String recordType,@RequestParam(defaultValue="") String searchText,@RequestParam(required=false) Integer profileId,Model model) {
        validType(recordType);String term="%"+searchText.strip()+"%";
        model.addAttribute("personNamePattern",NameRules.PERSON_PATTERN);
        model.addAttribute("organizationNamePattern",NameRules.ORGANIZATION_PATTERN);
        var rows="GUEST".equals(recordType)
            ?jdbc.queryForList("SELECT guest_id AS profile_id,title,first_name,last_name,card_type,official_id,phone,CONCAT_WS(' ',title,first_name,last_name) AS guest_name FROM guest WHERE CONCAT_WS(' ',title,first_name,last_name) LIKE ? OR last_name LIKE ? OR phone LIKE ? OR official_id=? ORDER BY guest_id DESC",term,term,term,searchText)
            :jdbc.queryForList("SELECT recipient_id AS profile_id,recipient_type,recipient_name,tax_id,address FROM document_recipient WHERE recipient_name LIKE ? OR tax_id=? ORDER BY recipient_id DESC",term,searchText);
        model.addAttribute("activePage","profiles");model.addAttribute("recordType",recordType);model.addAttribute("searchText",searchText);model.addAttribute("profiles",rows);
        Map<String,Object> selected=profileId==null?Map.of():read(recordType,profileId);
        model.addAttribute("profile",selected);model.addAttribute("input",formValues(selected));model.addAttribute("profileId",profileId);
        return "profiles";
    }
    @PostMapping("/profiles/save")
    public String save(@RequestParam MultiValueMap<String,String> input,Model model,HttpServletResponse response) {
        String type=value(input,"recordType");validType(type);
        try {
            Integer profileId=optionalId(value(input,"profileId"));
            int saved=Objects.requireNonNull(transaction.execute(tx -> {
                int id;
                if("GUEST".equals(type)) {
                    String title=text(input,"title",10,false);
                    check(List.of("","นาย","นาง","นางสาว").contains(title),400,"เลือกคำนำหน้าชื่อให้ถูกต้อง");
                    String first=text(input,"firstName",100,true),last=text(input,"lastName",100,true),card=text(input,"cardType",20,true),official=text(input,"officialId",50,true),phone=text(input,"phone",30,false);
                    check(NameRules.person(first),400,"ชื่อต้องมีตัวอักษรและไม่มีตัวเลข ใช้เว้นวรรค จุด ขีด หรืออัญประกาศในชื่อได้");
                    check(NameRules.person(last),400,"นามสกุลต้องมีตัวอักษรและไม่มีตัวเลข ใช้เว้นวรรค จุด ขีด หรืออัญประกาศในชื่อได้");
                    check(List.of("NATIONAL_ID","PASSPORT").contains(card),400,"เลือกประเภทเอกสารประจำตัวให้ถูกต้อง");
                    check(official.matches("[0-9]+"),400,"เลขบัตรประชาชนหรือหนังสือเดินทางต้องเป็นตัวเลข 0–9 เท่านั้น");
                    if(profileId==null) id=insert("INSERT INTO guest(title,first_name,last_name,card_type,official_id,phone) VALUES (?,?,?,?,?,?)",nullIfBlank(title),first,last,card,official,nullIfBlank(phone));
                    else {changed(jdbc.update("UPDATE guest SET title=?,first_name=?,last_name=?,card_type=?,official_id=?,phone=? WHERE guest_id=?",nullIfBlank(title),first,last,card,official,nullIfBlank(phone),profileId));id=profileId;}
                } else {
                    String kind=text(input,"recipientType",20,true),name=text(input,"recipientName",255,true),tax=text(input,"taxId",30,false),address=text(input,"address",2000,false);
                    check(List.of("PERSON","ORGANIZATION").contains(kind),400,"เลือกบุคคลหรือหน่วยงาน");
                    check("ORGANIZATION".equals(kind)?NameRules.organization(name):NameRules.person(name),400,
                            "ORGANIZATION".equals(kind)?"ชื่อหน่วยงานต้องมีตัวอักษร ไม่ใช่ตัวเลขหรือสัญลักษณ์ล้วน":"ชื่อผู้รับต้องมีตัวอักษรและไม่มีตัวเลข");
                    if(profileId==null) id=insert("INSERT INTO document_recipient(recipient_type,recipient_name,tax_id,address) VALUES (?,?,?,?)",kind,name,nullIfBlank(tax),nullIfBlank(address));
                    else {changed(jdbc.update("UPDATE document_recipient SET recipient_type=?,recipient_name=?,tax_id=?,address=? WHERE recipient_id=?",kind,name,nullIfBlank(tax),nullIfBlank(address),profileId));id=profileId;}
                }
                return number(read(type,id),"profile_id"); // read/map before commit
            }));
            return "redirect:/profiles?recordType="+type+"&profileId="+saved+"&saved=true";
        } catch(DeskProblem e) {
            response.setStatus(e.status);profiles(type,"",null,model);model.addAttribute("input",input.toSingleValueMap());model.addAttribute("profileId",value(input,"profileId"));model.addAttribute("formError",e.getMessage());return "profiles";
        }
    }
    private void validType(String type) {check(List.of("GUEST","DOCUMENT_RECIPIENT").contains(type),400,"เลือกประเภทข้อมูลให้ถูกต้อง");}
    private Map<String,Object> read(String type,int id) {
        return "GUEST".equals(type)?one("SELECT guest_id AS profile_id,title,first_name,last_name,card_type,official_id,phone,CONCAT_WS(' ',title,first_name,last_name) AS guest_name FROM guest WHERE guest_id=?",id):one("SELECT recipient_id AS profile_id,recipient_type,recipient_name,tax_id,address FROM document_recipient WHERE recipient_id=?",id);
    }
    private Map<String,Object> formValues(Map<String,Object> row) {
        Map<String,Object> values=new HashMap<>();
        Map.of("title","title","firstName","first_name","lastName","last_name","cardType","card_type","officialId","official_id","phone","phone","recipientType","recipient_type","recipientName","recipient_name","taxId","tax_id","address","address").forEach((field,column)->values.put(field,Objects.requireNonNullElse(row.get(column),"")));
        return values;
    }
}
