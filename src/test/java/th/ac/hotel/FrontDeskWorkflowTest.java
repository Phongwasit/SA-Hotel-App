package th.ac.hotel;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import th.ac.hotel.controller.CheckInController;
import th.ac.hotel.security.StaffPrincipal;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL commits/rollback; fixture rows belong only to this test's random staff account. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class FrontDeskWorkflowTest {
    @Autowired MockMvc mvc;
    @MockitoSpyBean JdbcTemplate jdbc;
    int staff,guest,recipient;
    List<Integer> guests=new ArrayList<>(),recipients=new ArrayList<>();
    Map<Integer,Map<String,Object>> roomBefore=new HashMap<>();
    LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Bangkok")).withNano(0);}
    LocalDate night(){return CheckInController.chargedNight(now());}
    @BeforeEach void fixture() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("hotel_frontdesk_test");
        staff=insert("INSERT INTO employee(first_name,last_name,position,username,password_hash) VALUES ('Workflow','Test','Front Desk',?,'{noop}unused')","flow_"+UUID.randomUUID().toString().substring(0,12));
        guest=insert("INSERT INTO guest(first_name,last_name,card_type,official_id,phone) VALUES ('Workflow','Guest','PASSPORT',?,'0800000000')",UUID.randomUUID().toString());guests.add(guest);
        recipient=newRecipient("หน่วยงานทดสอบ");
    }
    int newRecipient(String name){int id=insert("INSERT INTO document_recipient(recipient_type,recipient_name,tax_id,address) VALUES ('ORGANIZATION',?,'0100000000000','ที่อยู่ทดสอบ')",name);recipients.add(id);return id;}
    void remember(String number){int id=room(number);roomBefore.computeIfAbsent(id,key->jdbc.queryForMap("SELECT * FROM room WHERE room_id=?",id));}
    @AfterEach void cleanup() {
        reset(jdbc);
        var stays=jdbc.queryForList("SELECT stay_id FROM stay WHERE staff_id=?",Integer.class,staff);
        Set<Integer> refs=new HashSet<>();for(int stay:stays)refs.addAll(jdbc.queryForList("SELECT reference_id FROM receipt_stay WHERE stay_id=?",Integer.class,stay));
        for(int ref:refs){jdbc.update("DELETE FROM receipt_payment WHERE reference_id=?",ref);jdbc.update("DELETE FROM receipt_stay WHERE reference_id=?",ref);jdbc.update("DELETE FROM receipt_reference WHERE reference_id=?",ref);}
        for(int stay:stays){jdbc.update("DELETE FROM payment_allocation WHERE stay_id=?",stay);jdbc.update("DELETE FROM stay_charge WHERE stay_id=?",stay);jdbc.update("DELETE FROM stay WHERE stay_id=?",stay);}
        var payments=jdbc.queryForList("SELECT payment_id FROM payment WHERE staff_id=?",Integer.class,staff);
        for(int p:payments){jdbc.update("DELETE FROM payment_method WHERE payment_id=?",p);jdbc.update("DELETE FROM payment WHERE payment_id=?",p);}
        var bookings=jdbc.queryForList("SELECT booking_id FROM booking WHERE staff_id=?",Integer.class,staff);
        for(int b:bookings){jdbc.update("DELETE FROM booking_history WHERE booking_id=?",b);jdbc.update("DELETE FROM booking_item WHERE booking_id=?",b);jdbc.update("DELETE FROM booking WHERE booking_id=?",b);}
        for(var entry:roomBefore.entrySet()){var r=entry.getValue();jdbc.update("UPDATE room SET occupancy=?,cleanliness=?,usability=?,condition_reason=? WHERE room_id=?",r.get("occupancy"),r.get("cleanliness"),r.get("usability"),r.get("condition_reason"),entry.getKey());}
        for(int id:guests)jdbc.update("DELETE FROM guest WHERE guest_id=?",id);
        for(int id:recipients)jdbc.update("DELETE FROM document_recipient WHERE recipient_id=?",id);
        jdbc.update("DELETE FROM employee WHERE employee_id=?",staff);
    }
    int insert(String sql,Object...args){var key=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},key);return key.getKey().intValue();}
    int room(String n){return jdbc.queryForObject("SELECT room_id FROM room WHERE room_number=?",Integer.class,n);}
    MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request){return request.with(authentication(UsernamePasswordAuthenticationToken.authenticated(new StaffPrincipal(staff,"workflow-test","Workflow Test"),null,List.of(new SimpleGrantedAuthority("ROLE_STAFF")))));}
    MockHttpServletRequestBuilder checkin(int nights,String...numbers){for(String n:numbers)remember(n);return auth(post("/check-in/save").with(csrf()).param("guestId",""+guest).param("recipientId",""+recipient).param("keyCount","1").param("roomIds",Arrays.stream(numbers).map(n->""+room(n)).toArray(String[]::new)).param("checkInDate",night().toString()).param("checkOutDate",night().plusDays(nights).toString()));}
    List<Integer> arrive(int nights,String...numbers) throws Exception{mvc.perform(checkin(nights,numbers)).andExpect(status().is3xxRedirection());return jdbc.queryForList("SELECT stay_id FROM stay WHERE staff_id=? ORDER BY stay_id",Integer.class,staff);}
    List<Integer> charges(int stay){return jdbc.queryForList("SELECT charge_id FROM stay_charge WHERE stay_id=? ORDER BY charge_date",Integer.class,stay);}
    MockHttpServletRequestBuilder pay(Map<Integer,String> methods){var request=auth(post("/payments/save").with(csrf()).param("chargeIds",methods.keySet().stream().map(String::valueOf).toArray(String[]::new)).param("receivedAt",now().toString()).param("evidence","fixture"));methods.forEach((id,method)->request.param("method_"+id,method));return request;}
    int paid(Map<Integer,String> methods) throws Exception{var result=mvc.perform(pay(methods)).andExpect(status().is3xxRedirection()).andReturn();return Integer.parseInt(result.getResponse().getRedirectedUrl().split("/")[2]);}
    void settle(List<Integer> stays) throws Exception{Map<Integer,String> methods=new LinkedHashMap<>();for(int stay:stays)for(int c:charges(stay))if(jdbc.queryForObject("SELECT COUNT(*) FROM payment_allocation WHERE charge_id=?",Integer.class,c)==0)methods.put(c,"CASH");paid(methods);}
    MockHttpServletRequestBuilder receipt(List<Integer> stays,int recipient,String book){Set<Integer> payments=new TreeSet<>();for(int s:stays)payments.addAll(jdbc.queryForList("SELECT payment_id FROM payment_allocation WHERE stay_id=?",Integer.class,s));return auth(post("/documents/save").with(csrf()).param("stayIds",stays.stream().map(String::valueOf).toArray(String[]::new)).param("paymentIds",payments.stream().map(String::valueOf).toArray(String[]::new)).param("recipientId",""+recipient).param("bookNumber",book).param("receiptNumber","1").param("receiptDate",now().toLocalDate().toString()));}
    int issue(List<Integer> stays,int recipient,String book)throws Exception{var result=mvc.perform(receipt(stays,recipient,book)).andExpect(status().is3xxRedirection()).andReturn();return Integer.parseInt(result.getResponse().getRedirectedUrl().split("[=&]")[1]);}
    MockHttpServletRequestBuilder checkout(int stay){return auth(post("/check-out/save").with(csrf()).param("stayId",""+stay).param("departureConfirmed","true").param("mattersResolved","true"));}

    @Test void pagesRequireLoginAndPostsRequireCsrf()throws Exception{
        for(String path:List.of("/profiles","/check-in","/stays","/payments","/documents","/check-out","/room-status","/reports/stays","/reports/cash"))mvc.perform(get(path)).andExpect(status().is3xxRedirection());
        for(String path:List.of("/profiles/save","/check-in/save","/payments/save","/documents/save","/check-out/save","/room-status/save"))mvc.perform(auth(post(path))).andExpect(status().isForbidden());
    }
    @Test void allNewScreensRenderWithRealJdbcData()throws Exception{
        int stay=arrive(1,"2401").getFirst();
        for(String path:List.of("/profiles","/profiles?recordType=DOCUMENT_RECIPIENT","/profiles?profileId="+guest,"/check-in","/stays","/stays/"+stay,"/payments","/documents","/check-out?stayId="+stay,"/room-status?roomId="+room("2401"),"/reports/stays","/reports/cash"))mvc.perform(auth(get(path))).andExpect(status().isOk());
    }
    @Test void createAndEditGuestUsesBoundParametersAndRetainsId()throws Exception{
        var result=mvc.perform(auth(post("/profiles/save").with(csrf()).param("recordType","GUEST").param("firstName","O'Brien").param("lastName","ใหม่").param("cardType","PASSPORT").param("officialId","Test-123").param("phone","0801234567"))).andExpect(status().is3xxRedirection()).andReturn();
        int id=Integer.parseInt(result.getResponse().getRedirectedUrl().split("profileId=")[1].split("&")[0]);guests.add(id);
        mvc.perform(auth(post("/profiles/save").with(csrf()).param("recordType","GUEST").param("profileId",""+id).param("firstName","แก้แล้ว").param("lastName","เดิม").param("cardType","PASSPORT").param("officialId","Test-123"))).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT first_name FROM guest WHERE guest_id=?",String.class,id)).isEqualTo("แก้แล้ว");
        mvc.perform(auth(get("/profiles").param("searchText","' OR 1=1 --"))).andExpect(model().attribute("profiles",hasSize(0)));
    }
    @Test void guestTitlePersistsSeparatelyAndCanBeChangedOrCleared()throws Exception{
        for(String title:List.of("นาย","นาง","นางสาว","")) {
            mvc.perform(auth(post("/profiles/save").with(csrf()).param("recordType","GUEST").param("profileId",""+guest)
                    .param("title",title).param("firstName","ชื่อจริง").param("lastName","ทดสอบ").param("cardType","PASSPORT").param("officialId","Title-"+guest)))
                    .andExpect(status().is3xxRedirection());
            assertThat(jdbc.queryForObject("SELECT title FROM guest WHERE guest_id=?",String.class,guest)).isEqualTo(title.isEmpty()?null:title);
            assertThat(jdbc.queryForObject("SELECT first_name FROM guest WHERE guest_id=?",String.class,guest)).isEqualTo("ชื่อจริง");
            var result=mvc.perform(auth(get("/profiles").param("profileId",""+guest))).andExpect(status().isOk()).andReturn();
            assertThat(((Map<?,?>)result.getModelAndView().getModel().get("input")).get("title")).isEqualTo(title);
        }
    }
    @Test void guestTitleIsSavedOnCreateAndInvalidValuesDoNotChangeExistingProfile()throws Exception{
        var created=mvc.perform(auth(post("/profiles/save").with(csrf()).param("recordType","GUEST").param("title","นางสาว")
                .param("firstName","สร้างใหม่").param("lastName","ทดสอบ").param("cardType","PASSPORT").param("officialId","New-title-"+staff)))
                .andExpect(status().is3xxRedirection()).andReturn();
        int id=Integer.parseInt(created.getResponse().getRedirectedUrl().split("profileId=")[1].split("&")[0]);guests.add(id);
        assertThat(jdbc.queryForObject("SELECT title FROM guest WHERE guest_id=?",String.class,id)).isEqualTo("นางสาว");
        mvc.perform(auth(post("/profiles/save").with(csrf()).param("recordType","GUEST").param("profileId",""+id).param("title","INVALID")
                .param("firstName","ห้ามเปลี่ยน").param("lastName","ทดสอบ").param("cardType","PASSPORT").param("officialId","New-title-"+staff)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT title FROM guest WHERE guest_id=?",String.class,id)).isEqualTo("นางสาว");
        assertThat(jdbc.queryForObject("SELECT first_name FROM guest WHERE guest_id=?",String.class,id)).isEqualTo("สร้างใหม่");
        mvc.perform(auth(get("/profiles").param("searchText","นางสาว สร้างใหม่"))).andExpect(model().attribute("profiles",hasSize(1)));
    }
    @Test void guestTitleIsShownInCheckinStaysDocumentsAndReports()throws Exception{
        jdbc.update("UPDATE guest SET title='นางสาว' WHERE guest_id=?",guest);
        String name="นางสาว Workflow Guest";
        mvc.perform(auth(get("/check-in"))).andExpect(content().string(containsString(name)));
        int stay=arrive(1,"2401").getFirst();
        for(String route:List.of("/stays","/stays/"+stay,"/payments","/documents","/check-out"))
            mvc.perform(auth(get(route))).andExpect(status().isOk()).andExpect(content().string(containsString(name)));
        mvc.perform(auth(get("/reports/stays").param("generate","true").param("reportType","ARRIVAL").param("reportDate",now().toLocalDate().toString())))
                .andExpect(status().isOk()).andExpect(content().string(containsString(name)));
    }
    @Test void incompleteRecipientCanBeCreatedAndBadProfileKeepsEnteredValues()throws Exception{
        var result=mvc.perform(auth(post("/profiles/save").with(csrf()).param("recordType","DOCUMENT_RECIPIENT").param("recipientType","ORGANIZATION").param("recipientName","ยังไม่ครบ"))).andExpect(status().is3xxRedirection()).andReturn();
        int id=Integer.parseInt(result.getResponse().getRedirectedUrl().split("profileId=")[1].split("&")[0]);recipients.add(id);
        assertThat(jdbc.queryForMap("SELECT * FROM document_recipient WHERE recipient_id=?",id)).containsEntry("address",null).containsEntry("tax_id",null);
        mvc.perform(auth(post("/profiles/save").with(csrf()).param("recordType","GUEST").param("firstName","คงไว้"))).andExpect(status().isBadRequest()).andExpect(content().string(containsString("คงไว้")));
    }
    @Test void multiRoomCheckinUsesFixedNetRatesAndAuthenticatedStaff()throws Exception{
        var stays=arrive(2,"2401","1402","2606");assertThat(stays).hasSize(3);
        for(int id:stays){assertThat(charges(id)).hasSize(2);assertThat(jdbc.queryForObject("SELECT staff_id FROM stay WHERE stay_id=?",Integer.class,id)).isEqualTo(staff);}
        assertThat(jdbc.queryForObject("SELECT SUM(sc.amount) FROM stay_charge sc JOIN stay s ON s.stay_id=sc.stay_id WHERE s.staff_id=?",BigDecimal.class,staff)).isEqualByComparingTo("5800");
        assertThat(jdbc.queryForObject("SELECT occupancy FROM room WHERE room_number='2401'",String.class)).isEqualTo("OCCUPIED");
    }
    @Test void bookedCheckinConsumesOnlyMatchedSelectedItem()throws Exception{
        remember("2401");remember("2402");int b=insert("INSERT INTO booking(booker_name,phone,staff_id,created_at) VALUES ('B','080',?,?)",staff,now());
        int first=insert("INSERT INTO booking_item(booking_id,room_id,check_in_date,check_out_date,status) VALUES (?,?,?,?,'BOOKED')",b,room("2401"),night(),night().plusDays(1));
        int second=insert("INSERT INTO booking_item(booking_id,room_id,check_in_date,check_out_date,status) VALUES (?,?,?,?,'BOOKED')",b,room("2402"),night(),night().plusDays(1));
        mvc.perform(auth(get("/check-in").param("bookingId",""+b))).andExpect(status().isOk());
        mvc.perform(checkin(1,"2401").param("bookingItem_"+room("2401"),""+first)).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT status FROM booking_item WHERE booking_item_id=?",String.class,first)).isEqualTo("CHECKED_IN");
        assertThat(jdbc.queryForObject("SELECT status FROM booking_item WHERE booking_item_id=?",String.class,second)).isEqualTo("BOOKED");
        mvc.perform(checkin(1,"2402")).andExpect(status().isConflict());
    }
    @Test void oneUnreadyRoomRollsBackEntireMultiRoomCheckin()throws Exception{
        remember("2402");jdbc.update("UPDATE room SET cleanliness='DIRTY' WHERE room_number='2402'");
        mvc.perform(checkin(1,"2401","2402")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stay WHERE staff_id=?",Integer.class,staff)).isZero();
        assertThat(jdbc.queryForObject("SELECT occupancy FROM room WHERE room_number='2401'",String.class)).isEqualTo("VACANT");
    }
    @Test void malformedKeysDatesAndDuplicateRoomsAreRejected()throws Exception{
        mvc.perform(checkin(1,"2401").param("keyCount","3").with(r->{r.setParameter("keyCount","3");return r;})).andExpect(status().isBadRequest());
        mvc.perform(checkin(1,"2401").with(r->{r.setParameter("checkInDate",night().minusDays(1).toString());return r;})).andExpect(status().isBadRequest());
        mvc.perform(checkin(1,"2401","2401")).andExpect(status().isBadRequest());
    }
    @Test void earlyMorningUsesPreviousChargedNightAndNoonUsesToday(){
        assertThat(CheckInController.chargedNight(LocalDateTime.parse("2026-11-06T01:00"))).isEqualTo(LocalDate.parse("2026-11-05"));
        assertThat(CheckInController.chargedNight(LocalDateTime.parse("2026-11-06T12:00"))).isEqualTo(LocalDate.parse("2026-11-06"));
    }
    @Test void concurrentCheckinCanCommitOnlyOnce()throws Exception{
        remember("2401");var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        Callable<Integer> action=()->{start.await();return mvc.perform(checkin(1,"2401")).andReturn().getResponse().getStatus();};
        try{var a=pool.submit(action);var b=pool.submit(action);start.countDown();assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(302,409);}finally{pool.shutdownNow();}
    }
    @Test void installmentsAndMixedMethodsAllocateWholeNightsOnly()throws Exception{
        int stay=arrive(3,"2401").getFirst();var charges=charges(stay);
        paid(Map.of(charges.getFirst(),"CASH"));
        int p=paid(Map.of(charges.get(1),"TRANSFER",charges.get(2),"CASH"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment WHERE staff_id=?",Integer.class,staff)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM payment_allocation WHERE stay_id=?",BigDecimal.class,stay)).isEqualByComparingTo("2400");
        assertThat(jdbc.queryForList("SELECT amount FROM payment_method WHERE payment_id=?",BigDecimal.class,p)).allSatisfy(amount->assertThat(amount).isEqualByComparingTo("800"));
        mvc.perform(auth(get("/payments/"+p))).andExpect(status().isOk());
    }
    @Test void duplicateAndPreviouslyPaidNightsCannotBePaidAgain()throws Exception{
        int stay=arrive(2,"2401").getFirst();var charges=charges(stay);paid(Map.of(charges.getFirst(),"CASH"));
        mvc.perform(pay(Map.of(charges.getFirst(),"CASH",charges.getLast(),"TRANSFER"))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment WHERE staff_id=?",Integer.class,staff)).isEqualTo(1);
        mvc.perform(pay(Map.of(charges.getLast(),"CASH")).with(r->{r.setParameter("chargeIds",""+charges.getLast(),""+charges.getLast());return r;})).andExpect(status().isBadRequest());
    }
    @Test void concurrentPaymentCannotDoublePayANight()throws Exception{
        int stay=arrive(1,"2401").getFirst(),charge=charges(stay).getFirst();var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        Callable<Integer> action=()->{gate.await();return mvc.perform(pay(Map.of(charge,"CASH"))).andReturn().getResponse().getStatus();};
        try{var a=pool.submit(action);var b=pool.submit(action);gate.countDown();assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(302,409);}finally{pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_allocation WHERE charge_id=?",Integer.class,charge)).isEqualTo(1);
    }
    @Test void failedPaymentReadbackRollsBackAllMoneyRows()throws Exception{
        int stay=arrive(1,"2401").getFirst();
        doThrow(new CannotGetJdbcConnectionException("payment readback fixture")).when(jdbc).queryForList(eq("SELECT * FROM payment WHERE payment_id=?"),org.mockito.ArgumentMatchers.any(Object[].class));
        mvc.perform(pay(Map.of(charges(stay).getFirst(),"CASH"))).andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment WHERE staff_id=?",Integer.class,staff)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_allocation WHERE stay_id=?",Integer.class,stay)).isZero();
    }
    @Test void folioRequiresPaidChargesAndCompleteCurrentRecipient()throws Exception{
        int stay=arrive(1,"2401").getFirst();mvc.perform(auth(get("/documents").param("stayIds",""+stay))).andExpect(status().isConflict());settle(List.of(stay));
        jdbc.update("UPDATE document_recipient SET tax_id=NULL WHERE recipient_id=?",recipient);
        mvc.perform(auth(get("/documents").param("stayIds",""+stay))).andExpect(status().isBadRequest());
        jdbc.update("UPDATE document_recipient SET tax_id='0100000000000',recipient_name='ชื่อแก้ล่าสุด' WHERE recipient_id=?",recipient);
        mvc.perform(auth(get("/documents").param("stayIds",""+stay))).andExpect(status().isOk()).andExpect(content().string(containsString("ชื่อแก้ล่าสุด"))).andExpect(content().string(containsString("FOLIO")));
    }
    @Test void mixedOrMissingDefaultsRequireExplicitRecipient()throws Exception{
        var stays=arrive(1,"2401","2402");int other=newRecipient("อีกหน่วยงาน");jdbc.update("UPDATE stay SET default_recipient_id=? WHERE stay_id=?",other,stays.getLast());settle(stays);
        mvc.perform(auth(get("/documents").param("stayIds",stays.stream().map(String::valueOf).toArray(String[]::new)))).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/documents").param("stayIds",stays.stream().map(String::valueOf).toArray(String[]::new)).param("recipientId",""+recipient))).andExpect(status().isOk());
        jdbc.update("UPDATE stay SET default_recipient_id=NULL WHERE stay_id=?",stays.getFirst());mvc.perform(auth(get("/documents").param("stayIds",""+stays.getFirst()))).andExpect(status().isBadRequest());
    }
    @Test void finalReceiptCombinesInstallmentsAndEditDoesNotChangeStayDefault()throws Exception{
        int stay=arrive(2,"2401").getFirst();var c=charges(stay);paid(Map.of(c.getFirst(),"CASH"));paid(Map.of(c.getLast(),"TRANSFER"));
        int reference=issue(List.of(stay),recipient,"FLOW-"+staff);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM receipt_payment WHERE reference_id=?",Integer.class,reference)).isEqualTo(2);
        mvc.perform(receipt(List.of(stay),recipient,"DUP-"+staff)).andExpect(status().isConflict());
        int other=newRecipient("ผู้รับเฉพาะใบ");mvc.perform(receipt(List.of(stay),other,"EDIT-"+staff).param("referenceId",""+reference)).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT default_recipient_id FROM stay WHERE stay_id=?",Integer.class,stay)).isEqualTo(recipient);
        mvc.perform(auth(get("/documents").param("referenceId",""+reference))).andExpect(status().isOk()).andExpect(content().string(containsString("ผู้รับเฉพาะใบ")));
        jdbc.update("UPDATE document_recipient SET recipient_name='ล่าสุดอีกครั้ง' WHERE recipient_id=?",other);
        mvc.perform(auth(get("/documents").param("referenceId",""+reference))).andExpect(content().string(containsString("ล่าสุดอีกครั้ง")));
    }
    @Test void foreignOrOmittedPaymentLinksCannotIssueReceipt()throws Exception{
        int stay=arrive(1,"2401").getFirst();settle(List.of(stay));
        mvc.perform(receipt(List.of(stay),recipient,"BAD-"+staff).with(r->{r.setParameter("paymentIds","2147483647");return r;})).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM receipt_stay WHERE stay_id=?",Integer.class,stay)).isZero();
    }
    @Test void checkoutRequiresPaidBalanceAndConfirmedDeparture()throws Exception{
        int stay=arrive(1,"2401").getFirst();mvc.perform(checkout(stay)).andExpect(status().isConflict());settle(List.of(stay));
        mvc.perform(checkout(stay).with(r->{r.setParameter("mattersResolved","false");return r;})).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT status FROM stay WHERE stay_id=?",String.class,stay)).isEqualTo("ACTIVE");
    }
    @Test void checkoutMakesVacantDirtyAndPreservesOutOfOrderAndHistory()throws Exception{
        int stay=arrive(1,"2401").getFirst();settle(List.of(stay));jdbc.update("UPDATE room SET usability='OUT_OF_ORDER' WHERE room_number='2401'");
        mvc.perform(checkout(stay)).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForMap("SELECT occupancy,cleanliness,usability FROM room WHERE room_number='2401'")).containsEntry("occupancy","VACANT").containsEntry("cleanliness","DIRTY").containsEntry("usability","OUT_OF_ORDER");
        assertThat(jdbc.queryForObject("SELECT actual_check_out FROM stay WHERE stay_id=?",java.sql.Timestamp.class,stay)).isNotNull();
        mvc.perform(checkout(stay)).andExpect(status().isConflict());
    }
    @Test void failedCheckoutReadbackRollsBackBothStayAndRoom()throws Exception{
        int stay=arrive(1,"2401").getFirst();settle(List.of(stay));
        doAnswer(inv->{var rows=(List<Map<String,Object>>)inv.callRealMethod();if(!rows.isEmpty()&&"CHECKED_OUT".equals(rows.getFirst().get("status")))throw new CannotGetJdbcConnectionException("checkout readback fixture");return rows;}).when(jdbc).queryForList(argThat(sql->sql.startsWith("SELECT s.*")&&sql.endsWith(" WHERE s.stay_id=?")),org.mockito.ArgumentMatchers.any(Object[].class));
        mvc.perform(checkout(stay)).andExpect(status().isServiceUnavailable());reset(jdbc);
        assertThat(jdbc.queryForObject("SELECT status FROM stay WHERE stay_id=?",String.class,stay)).isEqualTo("ACTIVE");assertThat(jdbc.queryForObject("SELECT occupancy FROM room WHERE room_number='2401'",String.class)).isEqualTo("OCCUPIED");
    }
    @Test void roomConditionCannotChangeOccupancyAndReopeningDoesNotCleanIt()throws Exception{
        int stay=arrive(1,"2401").getFirst();
        mvc.perform(auth(post("/room-status/save").with(csrf()).param("roomId",""+room("2401")).param("cleanliness","DIRTY").param("usability","OUT_OF_ORDER").param("reason","แอร์เสีย").param("occupancy","VACANT"))).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT occupancy FROM room WHERE room_number='2401'",String.class)).isEqualTo("OCCUPIED");
        mvc.perform(auth(post("/room-status/save").with(csrf()).param("roomId",""+room("2401")).param("cleanliness","DIRTY").param("usability","USABLE").param("reason","ช่างแจ้งซ่อมแล้ว"))).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT cleanliness FROM room WHERE room_number='2401'",String.class)).isEqualTo("DIRTY");
    }
    @Test void recipientMayBeFilledDuringStayButNotAfterCheckout()throws Exception{
        int stay=arrive(1,"2401").getFirst();int other=newRecipient("หน่วยงานใหม่");
        mvc.perform(auth(post("/stays/"+stay+"/recipient").with(csrf()).param("recipientId",""+other))).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT default_recipient_id FROM stay WHERE stay_id=?",Integer.class,stay)).isEqualTo(other);
        settle(List.of(stay));mvc.perform(checkout(stay)).andExpect(status().is3xxRedirection());
        mvc.perform(auth(post("/stays/"+stay+"/recipient").with(csrf()).param("recipientId",""+recipient))).andExpect(status().isConflict());
    }
    @Test void stayReportsUseActualArrivalAndInclusiveExclusiveInHouseBoundaries()throws Exception{
        int stay=arrive(1,"2401").getFirst();jdbc.update("UPDATE stay SET check_in_date='2020-01-05',expected_check_out_date='2020-01-06',actual_check_in='2020-01-06 01:00:00',actual_check_out='2020-01-06 12:00:00',status='CHECKED_OUT' WHERE stay_id=?",stay);
        mvc.perform(auth(get("/reports/stays").param("generate","true").param("reportType","ARRIVAL").param("reportDate","2020-01-06"))).andExpect(status().isOk()).andExpect(model().attribute("reportRows",hasSize(1)));
        mvc.perform(auth(get("/reports/stays").param("generate","true").param("reportType","IN_HOUSE").param("asOf","2020-01-06T01:00:00"))).andExpect(model().attribute("reportRows",hasSize(1)));
        mvc.perform(auth(get("/reports/stays").param("generate","true").param("reportType","IN_HOUSE").param("asOf","2020-01-06T12:00:00"))).andExpect(model().attribute("reportRows",hasSize(0)));
        mvc.perform(auth(get("/reports/stays").param("generate","true").param("reportType","EXPECTED_DEPARTURE").param("reportDate","2020-01-06"))).andExpect(model().attribute("reportRows",hasSize(0)));
    }
    @Test void cashReportDoesNotMultiplyIdenticalAmountsOrReceiptLinksAndExcludesTransferRooms()throws Exception{
        var stays=arrive(1,"2401","2402","1401");Map<Integer,String> methods=new LinkedHashMap<>();methods.put(charges(stays.get(0)).getFirst(),"CASH");methods.put(charges(stays.get(1)).getFirst(),"CASH");methods.put(charges(stays.get(2)).getFirst(),"TRANSFER");int p=paid(methods);
        for(int i=0;i<stays.size();i++)issue(List.of(stays.get(i)),recipient,"CASH-"+staff+"-"+i);
        var result=mvc.perform(auth(get("/reports/cash").param("generate","true").param("startAt",now().toLocalDate().atStartOfDay().toString()).param("endAt",now().toLocalDate().plusDays(1).atStartOfDay().toString()))).andExpect(status().isOk()).andExpect(model().attribute("reportRows",hasSize(1))).andReturn();
        assertThat((BigDecimal)result.getModelAndView().getModel().get("total")).isEqualByComparingTo("1600");
        var row=((List<th.ac.hotel.controller.CashReportController.CashRow>)result.getModelAndView().getModel().get("reportRows")).getFirst();assertThat(row.rooms()).hasSize(2);assertThat(row.receipts()).hasSize(2);assertThat(row.rooms()).allSatisfy(r->assertThat(r.get("room_number")).isNotEqualTo("1401"));
    }
    @Test void cashReportEndIsExclusiveAndEmptyReportIsValid()throws Exception{
        int stay=arrive(1,"2401").getFirst();int payment=paid(Map.of(charges(stay).getFirst(),"CASH"));var at=jdbc.queryForObject("SELECT received_at FROM payment WHERE payment_id=?",java.sql.Timestamp.class,payment).toLocalDateTime();
        mvc.perform(auth(get("/reports/cash").param("generate","true").param("startAt",at.minusHours(1).toString()).param("endAt",at.toString()))).andExpect(status().isOk()).andExpect(model().attribute("reportRows",hasSize(0)));
    }
    @Test void reportFailuresNeverRenderZeroOrStalePreview()throws Exception{
        doThrow(new CannotGetJdbcConnectionException("cash read fixture")).when(jdbc).queryForList(argThat(sql->sql.startsWith("SELECT p.payment_id,p.received_at")),org.mockito.ArgumentMatchers.any(Object[].class));
        mvc.perform(auth(get("/reports/cash").param("generate","true").param("startAt","2020-01-01T00:00").param("endAt","2020-01-02T00:00"))).andExpect(status().isServiceUnavailable()).andExpect(content().string(not(containsString("cash-report-preview"))));
        mvc.perform(auth(get("/reports/stays").param("generate","true").param("reportType","IN_HOUSE").param("asOf",now().plusDays(1).toString()))).andExpect(status().isBadRequest());
    }
}
