package th.ac.hotel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import th.ac.hotel.security.StaffPrincipal;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** No outer test transaction: the Controller really commits/rolls back on MySQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @MockitoSpyBean JdbcTemplate jdbc;
    int staffId;
    final List<Integer> staffIds = new ArrayList<>(), guestIds = new ArrayList<>();
    final Map<Integer,String> restoredUsability = new HashMap<>();

    @BeforeEach void fixture() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("hotel_frontdesk_test");
        staffId = employee();
    }
    @AfterEach void cleanupOnlyThisTestsRows() {
        for (var entry:restoredUsability.entrySet()) jdbc.update("UPDATE room SET usability=? WHERE room_id=?",entry.getValue(),entry.getKey());
        for (int staff:staffIds) {
            jdbc.update("DELETE FROM stay WHERE staff_id=?",staff);
            jdbc.update("DELETE FROM booking_history WHERE booking_id IN (SELECT booking_id FROM booking WHERE staff_id=?)",staff);
            jdbc.update("DELETE FROM booking_item WHERE booking_id IN (SELECT booking_id FROM booking WHERE staff_id=?)",staff);
            jdbc.update("DELETE FROM booking WHERE staff_id=?",staff);
        }
        for(int guest:guestIds) jdbc.update("DELETE FROM guest WHERE guest_id=?",guest);
        for(int staff:staffIds) jdbc.update("DELETE FROM employee WHERE employee_id=?",staff);
    }
    int employee() {
        int id=insert("INSERT INTO employee(first_name,last_name,position,username,password_hash) VALUES ('ทดสอบ','การจอง','Front Desk',?,?)", "book_"+UUID.randomUUID().toString().substring(0,12),encoder.encode("Local-booking-test-password!"));
        staffIds.add(id); return id;
    }
    MockHttpServletRequestBuilder asStaff(MockHttpServletRequestBuilder request) {
        return request.with(authentication(UsernamePasswordAuthenticationToken.authenticated(new StaffPrincipal(staffId,"booking-test","พนักงาน ทดสอบ"),null,List.of(new SimpleGrantedAuthority("ROLE_STAFF")))));
    }
    MockHttpServletRequestBuilder create(String...numbers) {
        return asStaff(post("/bookings/save").with(csrf()).param("action","CREATE").param("bookerName","ผู้จองทดสอบ").param("phone","0800000000")
                .param("roomIds",Arrays.stream(numbers).map(n->String.valueOf(roomId(n))).toArray(String[]::new))
                .param("itemIds",Collections.nCopies(numbers.length,"").toArray(String[]::new))
                .param("checkInDates",Collections.nCopies(numbers.length,"2032-07-10").toArray(String[]::new))
                .param("checkOutDates",Collections.nCopies(numbers.length,"2032-07-12").toArray(String[]::new)));
    }
    int createBooking(String...rooms) throws Exception {
        var result=mvc.perform(create(rooms)).andExpect(status().is3xxRedirection()).andReturn();
        return Integer.parseInt(result.getResponse().getRedirectedUrl().split("[/?]")[2]);
    }
    MockHttpServletRequestBuilder edit(int id,List<Integer> kept,List<Integer> removed,String...numbers) {
        var request=asStaff(post("/bookings/save").with(csrf()).param("action","EDIT").param("bookingId",String.valueOf(id)).param("bookerName","ชื่อผู้จองใหม่").param("phone","0811111111"));
        if(numbers.length>0) request.param("roomIds",Arrays.stream(numbers).map(n->String.valueOf(roomId(n))).toArray(String[]::new))
                .param("itemIds",kept.stream().map(i->i==0?"":String.valueOf(i)).toArray(String[]::new))
                .param("checkInDates",Collections.nCopies(numbers.length,"2032-07-10").toArray(String[]::new))
                .param("checkOutDates",Collections.nCopies(numbers.length,"2032-07-12").toArray(String[]::new));
        if(!removed.isEmpty()) request.param("removedItemIds",removed.stream().map(String::valueOf).toArray(String[]::new));
        return request;
    }
    MockHttpServletRequestBuilder cancel(int id) { return asStaff(post("/bookings/save").with(csrf()).param("action","CANCEL").param("bookingId",String.valueOf(id))); }
    List<Integer> items(int id) { return jdbc.queryForList("SELECT booking_item_id FROM booking_item WHERE booking_id=? AND status='BOOKED' ORDER BY booking_item_id",Integer.class,id); }
    int roomId(String number) { return jdbc.queryForObject("SELECT room_id FROM room WHERE room_number=?",Integer.class,number); }
    int count(String table,int id) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE booking_id=?",Integer.class,id); }
    int insert(String sql,Object...args) {var key=new GeneratedKeyHolder();jdbc.update(connection->{var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);return statement;},key);return key.getKey().intValue();}

    @Test void protectedPagesAndCsrf() throws Exception {
        mvc.perform(get("/bookings")).andExpect(status().is3xxRedirection());
        mvc.perform(asStaff(post("/bookings/save"))).andExpect(status().isForbidden());
    }
    @Test void createMultipleRoomsUsesAuthenticatedCreatorAndReadbackRenders() throws Exception {
        int id=createBooking("2401","1402");
        assertThat(count("booking_item",id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT staff_id FROM booking WHERE booking_id=?",Integer.class,id)).isEqualTo(staffId);
        assertThat(count("booking_history",id)).isZero();
        mvc.perform(asStaff(get("/bookings/"+id))).andExpect(status().isOk()).andExpect(content().string(containsString("2401"))).andExpect(content().string(containsString("1402")));
        mvc.perform(asStaff(get("/bookings/"+id+"/edit"))).andExpect(status().isOk());
        mvc.perform(asStaff(get("/bookings/new").param("roomIds",String.valueOf(roomId("2402"))).param("checkInDate","2032-08-01").param("checkOutDate","2032-08-02"))).andExpect(status().isOk());
    }
    @Test void bookingDoesNotChangePhysicalRoomStatesAndUc01SeesIt() throws Exception {
        var before=jdbc.queryForList("SELECT occupancy,cleanliness,usability FROM room WHERE room_number='2401'");
        createBooking("2401");
        assertThat(jdbc.queryForList("SELECT occupancy,cleanliness,usability FROM room WHERE room_number='2401'")).isEqualTo(before);
        mvc.perform(asStaff(post("/rooms/search").with(csrf()).param("roomType","Standard").param("checkInDate","2032-07-10").param("checkOutDate","2032-07-12").param("requiredRooms","1")))
                .andExpect(status().isOk()).andExpect(model().attribute("availableCount",26));
    }
    @Test void searchMatchesNamePhoneAndReferenceWithoutSqlInjection() throws Exception {
        int id=createBooking("2401");
        for(String term:List.of("ผู้จองทดสอบ","0800000000",String.valueOf(id))) mvc.perform(asStaff(get("/bookings").param("searchText",term))).andExpect(status().isOk()).andExpect(model().attribute("bookings",hasSize(1)));
        mvc.perform(asStaff(get("/bookings").param("searchText","' OR 1=1 --"))).andExpect(model().attribute("bookings",hasSize(0)));
    }
    @Test void anotherEmployeeCanEditWithoutOverwritingOriginalCreatorAndHistoryContainsBeforeValues() throws Exception {
        int creator=staffId, id=createBooking("2401"); var itemIds=items(id); staffId=employee();
        mvc.perform(edit(id,itemIds,List.of(),"2402")).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT staff_id FROM booking WHERE booking_id=?",Integer.class,id)).isEqualTo(creator);
        assertThat(jdbc.queryForObject("SELECT room_id FROM booking_item WHERE booking_id=?",Integer.class,id)).isEqualTo(roomId("2402"));
        var snapshot=json.readTree(jdbc.queryForObject("SELECT previous_details FROM booking_history WHERE booking_id=?",String.class,id));
        assertThat(snapshot.path("booking").path("bookerName").asText()).isEqualTo("ผู้จองทดสอบ");
        assertThat(snapshot.path("changed_by_staff_id").asInt()).isEqualTo(staffId);
    }
    @Test void editCanIncreaseRoomsAndDecreaseByCancellingRemovedItems() throws Exception {
        int id=createBooking("2401","2402"); var initial=items(id);
        mvc.perform(edit(id,List.of(initial.getFirst(),0),List.of(initial.getLast()),"2401","1401")).andExpect(status().is3xxRedirection());
        assertThat(count("booking_item",id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT status FROM booking_item WHERE booking_item_id=?",String.class,initial.getLast())).isEqualTo("CANCELLED");
        assertThat(items(id)).hasSize(2);
        assertThat(count("booking_history",id)).isEqualTo(1);
    }
    @Test void cancellationCancelsAllPendingItemsAndStoresCancelHistoryWithoutDeletingRows() throws Exception {
        int id=createBooking("2401","2402");
        mvc.perform(asStaff(get("/bookings/"+id+"/cancel"))).andExpect(status().isOk());
        mvc.perform(cancel(id).param("selectedBookingItemIds",String.valueOf(items(id).getFirst()))).andExpect(status().is3xxRedirection());
        assertThat(items(id)).isEmpty(); assertThat(count("booking_item",id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT action FROM booking_history WHERE booking_id=?",String.class,id)).isEqualTo("CANCEL");
        mvc.perform(cancel(id)).andExpect(status().isConflict());
        assertThat(count("booking_history",id)).isEqualTo(1);
    }
    @Test void reducingAllRoomsIsRejectedInFavorOfExplicitCancelAll() throws Exception {
        int id=createBooking("2401");
        mvc.perform(edit(id,List.of(),items(id))).andExpect(status().isBadRequest());
        assertThat(items(id)).hasSize(1); assertThat(count("booking_history",id)).isZero();
    }
    @Test void selfOverlappingRoomsAndExistingBookingsAreRejectedAtomically() throws Exception {
        mvc.perform(create("2401","2401")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE staff_id=?",Integer.class,staffId)).isZero();
        int id=createBooking("2401");
        mvc.perform(create("2401")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE staff_id=?",Integer.class,staffId)).isEqualTo(1);
        assertThat(count("booking_item",id)).isEqualTo(1);
    }
    @Test void outOfOrderRoomAndInvalidFieldsDoNotCreateOrphanBookings() throws Exception {
        int room=roomId("2401");restoredUsability.put(room,jdbc.queryForObject("SELECT usability FROM room WHERE room_id=?",String.class,room));
        jdbc.update("UPDATE room SET usability='OUT_OF_ORDER' WHERE room_id=?",room);
        mvc.perform(create("2401")).andExpect(status().isConflict());
        mvc.perform(create("2402").with(request->{request.setParameter("bookerName"," ");return request;})).andExpect(status().isBadRequest());
        mvc.perform(create("2402").with(request->{request.setParameter("checkOutDates","2032-07-09");return request;})).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE staff_id=?",Integer.class,staffId)).isZero();
    }
    @Test void readbackFailureRollsBackHeaderAndAllNewItems() throws Exception {
        doThrow(new CannotGetJdbcConnectionException("read-back failure fixture")).when(jdbc).queryForList(argThat(sql->sql.contains("WHERE b.booking_id=? ORDER BY bi.booking_item_id")),org.mockito.ArgumentMatchers.any(Object[].class));
        mvc.perform(create("2401","2402")).andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE staff_id=?",Integer.class,staffId)).isZero();
    }
    @Test void malformedInputAndMissingParallelFieldsReturnValidationInsteadOfServerError() throws Exception {
        mvc.perform(create("2401").with(request->{request.setParameter("checkInDates","not-a-date");return request;})).andExpect(status().isBadRequest());
        mvc.perform(create("2401").with(request->{request.setParameter("roomIds","not-an-id");return request;})).andExpect(status().isBadRequest());
        mvc.perform(create("2401").with(request->{request.removeParameter("itemIds");return request;})).andExpect(status().isBadRequest());
        mvc.perform(create("2401").with(request->{request.setParameter("action","DELETE");return request;})).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE staff_id=?",Integer.class,staffId)).isZero();
    }
    @Test void checkedInItemsCannotBeChangedAndCancelAllIsRejected() throws Exception {
        int id=createBooking("2401","2402");var original=items(id);int checked=original.getFirst();
        jdbc.update("UPDATE booking_item SET status='CHECKED_IN' WHERE booking_item_id=?",checked);
        mvc.perform(cancel(id)).andExpect(status().isConflict());
        mvc.perform(edit(id,List.of(checked,original.getLast()),List.of(),"1401","2402")).andExpect(status().isConflict());
        mvc.perform(edit(id,List.of(original.getLast()),List.of(),"1401")).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT status FROM booking_item WHERE booking_item_id=?",String.class,checked)).isEqualTo("CHECKED_IN");
        assertThat(jdbc.queryForObject("SELECT room_id FROM booking_item WHERE booking_item_id=?",Integer.class,checked)).isEqualTo(roomId("2401"));
    }
    @Test void aForeignItemIdCannotBeEditedOrRemoved() throws Exception {
        int first=createBooking("2401"), second=createBooking("2402");
        mvc.perform(edit(first,items(second),List.of(),"1401")).andExpect(status().isConflict());
        assertThat(count("booking_history",first)).isZero();
    }
    @Test void backToBackNewBookingIsAllowed() throws Exception {
        createBooking("2401");
        mvc.perform(create("2401").with(request->{request.setParameter("checkInDates","2032-07-12");request.setParameter("checkOutDates","2032-07-13");return request;})).andExpect(status().is3xxRedirection());
    }
    @Test void twoConcurrentRequestsForTheSameRoomCannotBothCommit() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        Callable<Integer> reserve=()->{ready.countDown();start.await();return mvc.perform(create("2401")).andReturn().getResponse().getStatus();};
        try {
            var one=pool.submit(reserve);var two=pool.submit(reserve);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(302,409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE staff_id=?",Integer.class,staffId)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
}
