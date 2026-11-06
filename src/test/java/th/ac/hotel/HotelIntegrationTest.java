package th.ac.hotel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import th.ac.hotel.model.Room;
import th.ac.hotel.security.StaffPrincipal;

import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual MySQL + JDBC + MVC templates + security. Every fixture rolls back. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HotelIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @MockitoSpyBean JdbcTemplate jdbc;
    int staffId;
    String username;
    static final String PASSWORD = "Test-password-for-this-fixture-2026!";

    @BeforeEach
    void staffAccount() {
        String schema = jdbc.queryForObject("SELECT DATABASE()", String.class);
        assertThat(schema).isEqualTo("hotel_frontdesk_test");
        username = "test_" + UUID.randomUUID().toString().substring(0, 12);
        staffId = insert("INSERT INTO employee(first_name,last_name,position,username,password_hash) VALUES (?,?,?,?,?)",
                "พนักงาน", "ทดสอบ", "Front Desk", username, encoder.encode(PASSWORD));
    }

    MockHttpServletRequestBuilder asStaff(MockHttpServletRequestBuilder request) {
        return request.with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                new StaffPrincipal(staffId, username, "พนักงาน ทดสอบ"), null, List.of(new SimpleGrantedAuthority("ROLE_STAFF")))));
    }

    MockHttpServletRequestBuilder search(String type, String in, String out, String count) {
        return asStaff(post("/rooms/search").with(csrf()).param("roomType", type).param("checkInDate", in)
                .param("checkOutDate", out).param("requiredRooms", count));
    }

    @Test
    void pagesRequireStaffSelectionAndStateChangingRequestsRequireCsrf() throws Exception {
        mvc.perform(get("/rooms")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("http://localhost/login"));
        mvc.perform(post("/login").param("employee", "reception1")).andExpect(status().isForbidden());
        mvc.perform(asStaff(post("/rooms/search"))).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({"reception1,reception01", "reception2,reception02", "reception3,reception03"})
    void selectingStaffRotatesSessionAndKeepsCorrectBookingAuthor(String choice, String accountName) throws Exception {
        new th.ac.hotel.config.EmployeeBootstrap(jdbc, encoder).run(null);
        int employeeId = jdbc.queryForObject("SELECT employee_id FROM employee WHERE username=?", Integer.class, accountName);
        MockHttpSession original = new MockHttpSession();
        String originalId = original.getId();
        var result = mvc.perform(post("/login").session(original).with(csrf()).param("employee", choice))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/")).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session.getId()).isNotEqualTo(originalId);
        assertThat(session.getAttribute("employeeId")).isEqualTo(employeeId);
        mvc.perform(get("/").session(session)).andExpect(status().isOk()).andExpect(content().string(containsString(choice)))
                .andExpect(content().string(not(containsString("password_hash"))));
        mvc.perform(get("/login").session(session)).andExpect(redirectedUrl("/"));
        int roomId = jdbc.queryForObject("SELECT room_id FROM room WHERE room_number='2401'", Integer.class);
        var saved = mvc.perform(post("/bookings/save").session(session).with(csrf()).param("action", "CREATE")
                        .param("bookerName", "Staff selection attribution").param("phone", "0800000000")
                        .param("roomIds", String.valueOf(roomId)).param("itemIds", "")
                        .param("checkInDates", "2037-01-10").param("checkOutDates", "2037-01-11"))
                .andExpect(status().is3xxRedirection()).andReturn();
        int bookingId = Integer.parseInt(saved.getResponse().getRedirectedUrl().split("[/?]")[2]);
        assertThat(jdbc.queryForObject("SELECT staff_id FROM booking WHERE booking_id=?", Integer.class, bookingId)).isEqualTo(employeeId);
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?logout"));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/rooms")).andExpect(status().is3xxRedirection());
    }

    @Test
    void invalidStaffSelectionAndSqlInjectionDoNotCreateIdentity() throws Exception {
        for (String name : List.of("", username, "reception4", "reception01", "' OR '1'='1")) {
            var result = mvc.perform(post("/login").with(csrf()).param("employee", name))
                    .andExpect(status().isOk()).andExpect(model().attribute("loginError", "กรุณาเลือกพนักงานจากรายการ")).andReturn();
            var session = result.getRequest().getSession(false);
            assertThat(session == null || session.getAttribute("employeeId") == null).isTrue();
        }
    }

    @Test
    void selectionPageNeedsNoPasswordAndBootstrapPreservesExistingAccounts() throws Exception {
        var bootstrap = new th.ac.hotel.config.EmployeeBootstrap(jdbc, encoder);
        bootstrap.run(null);
        var before = jdbc.queryForList("SELECT employee_id,username,password_hash FROM employee WHERE username IN ('reception01','reception02','reception03') ORDER BY username");
        bootstrap.run(null);
        assertThat(jdbc.queryForList("SELECT employee_id,username,password_hash FROM employee WHERE username IN ('reception01','reception02','reception03') ORDER BY username")).isEqualTo(before).hasSize(3);
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("เลือกพนักงาน")))
                .andExpect(content().string(containsString("reception3")))
                .andExpect(content().string(not(containsString("type=\"password\""))));
    }

    @Test
    void failedStaffLookupNeverCreatesIdentity() throws Exception {
        doThrow(new CannotGetJdbcConnectionException("offline fixture")).when(jdbc).queryForList(anyString(), eq("reception01"));
        var result = mvc.perform(post("/login").with(csrf()).param("employee", "reception1"))
                .andExpect(status().isServiceUnavailable()).andReturn();
        var session = result.getRequest().getSession(false);
        assertThat(session == null || session.getAttribute("employeeId") == null).isTrue();
    }

    @Test
    void inventoryMatchesConfirmedNumbersBedsAndRates() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM room", Integer.class)).isEqualTo(41);
        assertThat(jdbc.queryForList("SELECT room_number FROM room WHERE bed_type='DOUBLE' ORDER BY room_number", String.class))
                .containsExactly("1402", "1404", "2606", "2608");
        assertThat(jdbc.queryForObject("SELECT type_name FROM room WHERE room_number='2606'", String.class)).isEqualTo("Suite");
        mvc.perform(asStaff(get("/rooms"))).andExpect(status().isOk()).andExpect(model().attribute("inventoryCount", 41));
        mvc.perform(search("Standard", "2030-06-10", "2030-06-12", "1")).andExpect(model().attribute("availableCount", 27))
                .andExpect(content().string(containsString("800")));
        mvc.perform(search("Deluxe", "2030-06-10", "2030-06-12", "1")).andExpect(model().attribute("availableCount", 12))
                .andExpect(content().string(containsString("1,000")));
        mvc.perform(search("Suite", "2030-06-10", "2030-06-12", "1")).andExpect(model().attribute("availableCount", 2))
                .andExpect(content().string(containsString("1,100")));
    }

    @ParameterizedTest
    @CsvSource({"2030-06-09,2030-06-10,27", "2030-06-12,2030-06-13,27", "2030-06-09,2030-06-11,26",
            "2030-06-10,2030-06-12,26", "2030-06-11,2030-06-13,26", "2030-06-09,2030-06-13,26"})
    void bookedIntervalsBlockOverlapsButAllowBackToBackStays(String in, String out, int count) throws Exception {
        book("2401", "2030-06-10", "2030-06-12", "BOOKED");
        mvc.perform(search("Standard", in, out, "1")).andExpect(status().isOk()).andExpect(model().attribute("availableCount", count));
    }

    @Test
    void mapperGroupsMultipleBlockingRowsIntoOneRoom() throws Exception {
        book("2401", "2030-06-10", "2030-06-12", "BOOKED");
        book("2401", "2030-06-14", "2030-06-16", "BOOKED");
        mvc.perform(search("Standard", "2030-06-12", "2030-06-14", "1"))
                .andExpect(model().attribute("availableCount", 27)).andExpect(model().attribute("matchingRooms", hasSize(27)));
        mvc.perform(search("Standard", "2030-06-09", "2030-06-17", "1"))
                .andExpect(model().attribute("availableCount", 26)).andExpect(content().string(not(containsString("data-room=\"2401\""))));
    }

    @Test
    void cancelledAndCheckedInBookingItemsDoNotDoubleBlock() throws Exception {
        book("2401", "2030-06-10", "2030-06-12", "CANCELLED");
        int item = book("2402", "2030-06-10", "2030-06-12", "CHECKED_IN");
        stay("2402", "2030-06-10", "2030-06-12", item);
        mvc.perform(search("Standard", "2030-06-10", "2030-06-12", "1"))
                .andExpect(model().attribute("availableCount", 26)).andExpect(content().string(containsString("data-room=\"2401\"")))
                .andExpect(content().string(not(containsString("data-room=\"2402\""))));
    }

    @Test
    void activeStayBlocksOnlyItsDatesAndTodayDirtyOccupiedDoesNotExcludeFutureAvailability() throws Exception {
        stay("2401", "2030-06-01", "2030-06-03", null);
        jdbc.update("UPDATE room SET occupancy='OCCUPIED',cleanliness='DIRTY' WHERE room_number='2401'");
        mvc.perform(search("Standard", "2030-06-01", "2030-06-02", "1")).andExpect(model().attribute("availableCount", 26));
        mvc.perform(search("Standard", "2030-06-10", "2030-06-12", "1")).andExpect(model().attribute("availableCount", 27))
                .andExpect(content().string(containsString("รอทำความสะอาด")));
    }

    @Test
    void everySearchReloadsDatabaseEvenWithIdenticalCriteria() throws Exception {
        mvc.perform(search("Standard", "2030-06-10", "2030-06-12", "1")).andExpect(model().attribute("availableCount", 27));
        jdbc.update("UPDATE room SET usability='OUT_OF_ORDER' WHERE room_number='2401'");
        mvc.perform(search("Standard", "2030-06-10", "2030-06-12", "1")).andExpect(model().attribute("availableCount", 26));
    }

    @Test
    void requestedCountProducesShortfallWithoutLimitingMatchingRooms() throws Exception {
        mvc.perform(search("Standard", "2030-06-10", "2030-06-12", "30")).andExpect(status().isOk())
                .andExpect(model().attribute("shortfall", 3)).andExpect(model().attribute("matchingRooms", hasSize(27)));
    }

    @ParameterizedTest
    @CsvSource({"Standard,2030-06-10,2030-06-10,1", "Standard,2030-06-12,2030-06-10,1", "Standard,not-a-date,2030-06-12,1",
            "Standard,2030-06-10,2030-06-12,0", "Standard,2030-06-10,2030-06-12,-1", "Standard,2030-06-10,2030-06-12,1.5", "Family,2030-06-10,2030-06-12,1"})
    void rejectsInvalidSearchWithoutDisplayingAvailability(String type, String in, String out, String count) throws Exception {
        mvc.perform(search(type, in, out, count)).andExpect(status().isBadRequest()).andExpect(model().attribute("searched", false))
                .andExpect(model().attributeDoesNotExist("availableCount")).andExpect(content().string(not(containsString("id=\"available-count\""))));
    }

    @Test
    void noMatchesIsSuccessfulZeroRatherThanDatabaseFailure() throws Exception {
        jdbc.update("UPDATE room SET usability='OUT_OF_ORDER' WHERE type_name='Standard'");
        mvc.perform(search("Standard", "2030-06-10", "2030-06-12", "2")).andExpect(status().isOk())
                .andExpect(model().attribute("availableCount", 0)).andExpect(model().attribute("shortfall", 2));
    }

    @Test
    void failedLoadDoesNotReusePreviousResultsOrReportZeroAndHappensBeforeValidation() throws Exception {
        mvc.perform(search("Standard", "2030-06-10", "2030-06-12", "1")).andExpect(model().attribute("availableCount", 27));
        doThrow(new CannotGetJdbcConnectionException("offline fixture")).when(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<ResultSetExtractor<List<Room>>>any());
        mvc.perform(search("Standard", "2030-06-10", "2030-06-12", "1")).andExpect(status().isServiceUnavailable())
                .andExpect(model().attribute("searched", false)).andExpect(model().attributeDoesNotExist("availableCount"))
                .andExpect(content().string(not(containsString("data-room="))));
        mvc.perform(search("invalid", "invalid", "invalid", "0")).andExpect(status().isServiceUnavailable())
                .andExpect(model().attribute("loadSucceeded", false));
    }

    @Test
    void searchingDoesNotWriteBookingsStaysPaymentsOrRoomStates() throws Exception {
        String before = countsAndStates();
        mvc.perform(search("Standard", "2030-06-10", "2030-06-12", "1")).andExpect(status().isOk());
        assertThat(countsAndStates()).isEqualTo(before);
    }

    @Test
    void actualSchemaHasSixteenBusinessTablesEightyThreeFieldsAndTwentyTwoForeignKeys() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name <> 'flyway_schema_history'", Integer.class)).isEqualTo(16);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name <> 'flyway_schema_history'", Integer.class)).isEqualTo(84);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()", Integer.class)).isEqualTo(22);
    }

    @Test
    void recipientMayBeIncompleteAndAnAfterMidnightStayPreservesChargedDate() {
        int recipient = insert("INSERT INTO document_recipient(recipient_type,recipient_name,tax_id,address) VALUES ('ORGANIZATION',?,NULL,NULL)", "หน่วยงานทดสอบ");
        assertThat(jdbc.queryForObject("SELECT address FROM document_recipient WHERE recipient_id=?", String.class, recipient)).isNull();
        int stay = stay("2401", "2030-06-05", "2030-06-06", null);
        jdbc.update("UPDATE stay SET actual_check_in=?,default_recipient_id=? WHERE stay_id=?", "2030-06-06 01:00:00", recipient, stay);
        assertThat(jdbc.queryForObject("SELECT DATEDIFF(expected_check_out_date,check_in_date) FROM stay WHERE stay_id=?", Integer.class, stay)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT DATE(actual_check_in) FROM stay WHERE stay_id=?", LocalDate.class, stay)).isEqualTo(LocalDate.of(2030,6,6));
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+07:00");
    }

    @Test
    void allocationCannotPayAnotherStaysChargeOrAnotherPaymentsMethodOrTheSameNightTwice() {
        int firstStay = stay("2401", "2030-06-10", "2030-06-12", null);
        int secondStay = stay("2402", "2030-06-10", "2030-06-12", null);
        int firstCharge = insert("INSERT INTO stay_charge(stay_id,charge_date,amount) VALUES (?,?,800)", firstStay, "2030-06-10");
        int secondCharge = insert("INSERT INTO stay_charge(stay_id,charge_date,amount) VALUES (?,?,800)", secondStay, "2030-06-10");
        int firstPayment = insert("INSERT INTO payment(staff_id,received_at) VALUES (?,?)", staffId, "2030-06-10 14:00:00");
        int secondPayment = insert("INSERT INTO payment(staff_id,received_at) VALUES (?,?)", staffId, "2030-06-10 14:05:00");
        int firstMethod = insert("INSERT INTO payment_method(payment_id,method,amount) VALUES (?,'CASH',800)", firstPayment);
        int secondMethod = insert("INSERT INTO payment_method(payment_id,method,amount) VALUES (?,'TRANSFER',800)", secondPayment);
        String sql = "INSERT INTO payment_allocation(payment_id,stay_id,amount,charge_id,payment_method_id) VALUES (?,?,800,?,?)";
        assertThatThrownBy(() -> jdbc.update(sql, firstPayment, firstStay, secondCharge, firstMethod)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, firstPayment, firstStay, firstCharge, secondMethod)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(sql, firstPayment, firstStay, firstCharge, firstMethod);
        assertThatThrownBy(() -> jdbc.update(sql, secondPayment, firstStay, firstCharge, secondMethod)).isInstanceOf(DataIntegrityViolationException.class);
    }

    int book(String room, String in, String out, String status) {
        int booking = insert("INSERT INTO booking(booker_name,phone,staff_id,created_at) VALUES ('ผู้จองทดสอบ','000',?,?)", staffId, "2030-06-01 12:00:00");
        return insert("INSERT INTO booking_item(booking_id,room_id,check_in_date,check_out_date,status) VALUES (?,?,?,?,?)", booking, roomId(room), in, out, status);
    }

    int stay(String room, String in, String out, Integer bookingItemId) {
        int guest = insert("INSERT INTO guest(first_name,last_name,card_type,official_id) VALUES ('ผู้พัก','ทดสอบ','ID_CARD',?)", UUID.randomUUID().toString());
        return insert("INSERT INTO stay(room_id,guest_id,staff_id,booking_item_id,check_in_date,expected_check_out_date,actual_check_in,key_count,status) VALUES (?,?,?,?,?,?,?,1,'ACTIVE')",
                roomId(room), guest, staffId, bookingItemId, in, out, in + " 14:00:00");
    }

    int roomId(String room) { return jdbc.queryForObject("SELECT room_id FROM room WHERE room_number=?", Integer.class, room); }

    int insert(String sql, Object... args) {
        var holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i=0; i<args.length; i++) statement.setObject(i+1, args[i]);
            return statement;
        }, holder);
        return holder.getKey().intValue();
    }

    String countsAndStates() {
        return jdbc.queryForList("SELECT room_id,occupancy,cleanliness,usability FROM room ORDER BY room_id").toString()
                + jdbc.queryForObject("SELECT COUNT(*) FROM booking", Integer.class)
                + jdbc.queryForObject("SELECT COUNT(*) FROM stay", Integer.class)
                + jdbc.queryForObject("SELECT COUNT(*) FROM payment", Integer.class);
    }
}
