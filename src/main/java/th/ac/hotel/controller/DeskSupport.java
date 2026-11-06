package th.ac.hotel.controller;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.MultiValueMap;
import th.ac.hotel.model.StayAccount;

/** Shared bound-JDBC/validation helpers; each concrete UC Controller owns its transaction. */
public abstract class DeskSupport {
    protected final JdbcTemplate jdbc;
    protected final TransactionTemplate transaction;
    protected final Clock clock;
    protected static final String STAY_VIEW = """
        SELECT s.*,r.room_number,r.type_name,r.occupancy,r.cleanliness,r.usability,
               g.title,g.first_name,g.last_name,CONCAT_WS(' ',g.title,g.first_name,g.last_name) AS guest_name,g.official_id,g.card_type,g.phone,
               dr.recipient_name AS default_recipient_name
        FROM stay s JOIN room r ON r.room_id=s.room_id JOIN guest g ON g.guest_id=s.guest_id
        LEFT JOIN document_recipient dr ON dr.recipient_id=s.default_recipient_id
        """;
    protected DeskSupport(JdbcTemplate jdbc,PlatformTransactionManager manager,Clock clock) {
        this.jdbc=jdbc;this.clock=clock;this.transaction=new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    protected LocalDateTime now() { return LocalDateTime.now(clock).withNano(0); }
    protected int insert(String sql,Object...args) {
        var key=new GeneratedKeyHolder();
        jdbc.update(connection -> {var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);return statement;},key);
        return Objects.requireNonNull(key.getKey()).intValue();
    }
    protected Map<String,Object> one(String sql,Object...args) {
        var rows=jdbc.queryForList(sql,args);
        if(rows.size()!=1) throw new DeskProblem(409,"ไม่พบรายการ หรือข้อมูลเปลี่ยนไป กรุณาโหลดใหม่");
        return rows.getFirst();
    }
    protected StayAccount account(int id) {
        var stay=one(STAY_VIEW+" WHERE s.stay_id=?",id);
        return new StayAccount(stay,
            jdbc.queryForList("SELECT * FROM stay_charge WHERE stay_id=? ORDER BY charge_date,charge_id",id),
            jdbc.queryForList("SELECT pa.*,pm.method,p.received_at FROM payment_allocation pa JOIN payment_method pm ON pm.payment_method_id=pa.payment_method_id AND pm.payment_id=pa.payment_id JOIN payment p ON p.payment_id=pa.payment_id WHERE pa.stay_id=? ORDER BY pa.charge_id",id),
            jdbc.queryForList("SELECT rr.* FROM receipt_reference rr JOIN receipt_stay rs ON rs.reference_id=rr.reference_id WHERE rs.stay_id=? ORDER BY rr.reference_id",id));
    }
    protected List<StayAccount> findAccounts(String term,boolean activeOnly) {
        String pattern="%"+term.strip()+"%";
        var ids=jdbc.queryForList("SELECT s.stay_id FROM stay s JOIN room r ON r.room_id=s.room_id JOIN guest g ON g.guest_id=s.guest_id WHERE "+(activeOnly?"s.status='ACTIVE' AND ":"")+"(r.room_number LIKE ? OR CAST(s.stay_id AS CHAR)=? OR CONCAT_WS(' ',g.title,g.first_name,g.last_name) LIKE ? OR g.last_name LIKE ?) ORDER BY s.stay_id DESC",Integer.class,pattern,term.strip(),pattern,pattern);
        return ids.stream().map(this::account).toList();
    }
    /** Same order across payment, documents and checkout: sorted rooms, then sorted stays. */
    protected void lockAccounts(List<Integer> ids) {
        var roomIds=new TreeSet<Integer>();
        for(int id:ids) roomIds.add(number(one("SELECT room_id FROM stay WHERE stay_id=?",id),"room_id"));
        for(int id:roomIds) one("SELECT room_id FROM room WHERE room_id=? FOR UPDATE",id);
        for(int id:new TreeSet<>(ids)) one("SELECT stay_id FROM stay WHERE stay_id=? FOR UPDATE",id);
    }
    protected static int number(Map<String,Object> row,String key) { return ((Number)row.get(key)).intValue(); }
    protected static LocalDate date(Map<String,Object> row,String key) { return ((java.sql.Date)row.get(key)).toLocalDate(); }
    protected static String value(MultiValueMap<String,String> input,String key) { return Objects.requireNonNullElse(input.getFirst(key),"").strip(); }
    protected static String text(MultiValueMap<String,String> input,String key,int max,boolean required) {
        String value=value(input,key);
        check(value.length()<=max && (!required||!value.isBlank()),400,"กรอกข้อมูล "+key+" ให้ครบและไม่เกิน "+max+" ตัวอักษร");
        return value;
    }
    protected static int id(String value) {
        try {int id=Integer.parseInt(value);if(id>0)return id;}catch(NumberFormatException ignored){}
        throw new DeskProblem(400,"รหัสรายการต้องเป็นจำนวนเต็มมากกว่าศูนย์");
    }
    protected static Integer optionalId(String value) { return value==null||value.isBlank()?null:id(value); }
    protected static List<Integer> ids(List<String> values) {
        var result=values.stream().map(DeskSupport::id).toList();
        check(!result.isEmpty() && new HashSet<>(result).size()==result.size(),400,"เลือกรายการอย่างน้อยหนึ่งรายการและห้ามเลือกรายการซ้ำ");
        return result;
    }
    protected static LocalDate parseDate(String value) {
        try { return LocalDate.parse(value); } catch(DateTimeParseException|NullPointerException e) {throw new DeskProblem(400,"ระบุวันที่ให้ถูกต้อง");}
    }
    protected static LocalDateTime parseTime(String value) {
        try {return LocalDateTime.parse(value);}catch(DateTimeParseException|NullPointerException e){throw new DeskProblem(400,"ระบุวันเวลาให้ถูกต้อง");}
    }
    protected static String nullIfBlank(String text) { return text.isBlank()?null:text; }
    protected static void check(boolean ok,int status,String message) {if(!ok)throw new DeskProblem(status,message);}
    protected static void changed(int count) {check(count==1,409,"ข้อมูลเปลี่ยนไประหว่างทำรายการ กรุณาโหลดใหม่");}
    public static class DeskProblem extends RuntimeException {
        public final int status;
        public DeskProblem(int status,String message){super(message);this.status=status;}
    }
}
