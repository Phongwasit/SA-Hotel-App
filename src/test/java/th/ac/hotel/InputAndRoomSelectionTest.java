package th.ac.hotel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import th.ac.hotel.security.StaffPrincipal;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InputAndRoomSelectionTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    int staff;
    @BeforeEach void fixture() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("hotel_frontdesk_test");
        String username="input_"+UUID.randomUUID();
        jdbc.update("INSERT INTO employee(first_name,last_name,position,username,password_hash) VALUES ('Input','Test','Front Desk',?,'{noop}unused')",username);
        staff=jdbc.queryForObject("SELECT employee_id FROM employee WHERE username=?",Integer.class,username);
    }
    MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.with(authentication(UsernamePasswordAuthenticationToken.authenticated(new StaffPrincipal(staff,"input-test","Input Test"),null,List.of(new SimpleGrantedAuthority("ROLE_STAFF"))))).with(csrf());
    }
    String room(String number){return jdbc.queryForObject("SELECT room_id FROM room WHERE room_number=?",String.class,number);}
    MockHttpServletRequestBuilder search(String type) {
        return auth(post("/rooms/search").param("roomType",type).param("checkInDate","2038-01-10").param("checkOutDate","2038-01-12").param("requiredRooms","1"));
    }
    @Test void switchingRoomTypeKeepsSelectedOtherTypeAndPreparesBothRooms()throws Exception {
        mvc.perform(search("Deluxe").param("roomIds",room("2401")))
                .andExpect(status().isOk()).andExpect(model().attribute("selectedRooms",hasSize(1)))
                .andExpect(content().string(containsString("2401")));
        var result=mvc.perform(search("Deluxe").param("roomIds",room("2401"),room("1402")).param("next","booking"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String url=result.getResponse().getRedirectedUrl();
        assertThat(url).contains("roomIds="+room("2401"),"roomIds="+room("1402"));
        mvc.perform(auth(get(url))).andExpect(status().isOk()).andExpect(model().attribute("rows",hasSize(2)));
    }
    @ParameterizedTest @ValueSource(strings={"123456","๑๒๓","１２３","John2","---"})
    void numericOrInvalidPersonalNamesCannotBeSaved(String name)throws Exception {
        int guests=jdbc.queryForObject("SELECT COUNT(*) FROM guest",Integer.class);
        mvc.perform(auth(post("/profiles/save").param("recordType","GUEST").param("firstName",name).param("lastName","ทดสอบ").param("cardType","PASSPORT").param("officialId","1000000001")))
                .andExpect(status().isBadRequest()).andExpect(model().attributeExists("formError"));
        mvc.perform(auth(post("/profiles/save").param("recordType","GUEST").param("firstName","ชื่อจริง").param("lastName",name).param("cardType","PASSPORT").param("officialId","1000000002")))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guest",Integer.class)).isEqualTo(guests);
        int bookings=jdbc.queryForObject("SELECT COUNT(*) FROM booking",Integer.class);
        mvc.perform(auth(post("/bookings/save").param("action","CREATE").param("bookerName",name).param("phone","0800000000").param("roomIds",room("2401")).param("itemIds","").param("checkInDates","2038-01-10").param("checkOutDates","2038-01-12")))
                .andExpect(status().isBadRequest()).andExpect(model().attributeExists("formErrors"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking",Integer.class)).isEqualTo(bookings);
        mvc.perform(auth(post("/profiles/save").param("recordType","DOCUMENT_RECIPIENT").param("recipientType","PERSON").param("recipientName",name))).andExpect(status().isBadRequest());
    }
    @Test void organizationMayIncludeDigitsButCannotBeOnlyDigits()throws Exception {
        mvc.perform(auth(post("/profiles/save").param("recordType","DOCUMENT_RECIPIENT").param("recipientType","ORGANIZATION").param("recipientName","123456"))).andExpect(status().isBadRequest());
        mvc.perform(auth(post("/profiles/save").param("recordType","DOCUMENT_RECIPIENT").param("recipientType","ORGANIZATION").param("recipientName","บริษัท 123 จำกัด"))).andExpect(status().is3xxRedirection());
    }
    @Test void changingDatesClearsPicksBeforeBookingHandoff()throws Exception {
        mvc.perform(search("Deluxe").param("roomIds",room("2401")).param("selectionCheckInDate","2038-01-09").param("selectionCheckOutDate","2038-01-12").param("next","booking"))
                .andExpect(status().isBadRequest()).andExpect(model().attribute("selectedRooms",hasSize(0))).andExpect(model().attributeExists("selectionNotice","selectionError"));
    }
    @Test void staleSelectedRoomIsRemovedAndRequiresReviewNotSilentBooking()throws Exception {
        jdbc.update("INSERT INTO booking(booker_name,phone,staff_id,created_at) VALUES ('Other guest','0800000000',?,NOW())",staff);
        int booking=jdbc.queryForObject("SELECT MAX(booking_id) FROM booking WHERE staff_id=?",Integer.class,staff);
        jdbc.update("INSERT INTO booking_item(booking_id,room_id,check_in_date,check_out_date,status) VALUES (?,?,'2038-01-10','2038-01-12','BOOKED')",booking,Integer.parseInt(room("2401")));
        mvc.perform(search("Deluxe").param("roomIds",room("2401"),room("1402")).param("next","booking"))
                .andExpect(status().isBadRequest()).andExpect(model().attribute("selectedRooms",hasSize(1))).andExpect(model().attributeExists("selectionNotice","selectionError"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE staff_id=?",Integer.class,staff)).isEqualTo(1);
    }
    @Test void selectedRoomsCanBeRemovedAcrossFiltersAndDuplicatesDoNotMultiply()throws Exception {
        mvc.perform(search("Deluxe").param("roomIds",room("2401"),room("2401"),room("1402")).param("removeRoomId",room("2401")))
                .andExpect(status().isOk()).andExpect(model().attribute("selectedRooms",hasSize(1))).andExpect(model().attribute("hiddenSelectedRooms",hasSize(0)));
        mvc.perform(search("Deluxe").param("roomIds",room("2401"),room("2401")))
                .andExpect(status().isOk()).andExpect(model().attribute("selectedRooms",hasSize(1)));
    }
    @Test void forgedRoomIdCannotProceedAndInvalidDatesRetainDraftForCorrection()throws Exception {
        mvc.perform(search("Deluxe").param("roomIds","2147483647").param("next","booking")).andExpect(status().isBadRequest());
        mvc.perform(search("Deluxe").param("roomIds",room("2401")).with(request->{request.setParameter("checkOutDate","bad-date");return request;}))
                .andExpect(status().isBadRequest()).andExpect(model().attribute("selectedRooms",hasSize(1)));
    }
    @Test void editingInvalidNamesDoesNotWriteAndCancellingLegacyNumericBookerStillWorks()throws Exception {
        var created=mvc.perform(auth(post("/profiles/save").param("recordType","GUEST").param("firstName","ชื่อเดิม").param("lastName","ทดสอบ").param("cardType","PASSPORT").param("officialId","1000000003")))
                .andExpect(status().is3xxRedirection()).andReturn();
        String profile=created.getResponse().getRedirectedUrl().split("profileId=")[1].split("&")[0];
        mvc.perform(auth(post("/profiles/save").param("recordType","GUEST").param("profileId",profile).param("firstName","123").param("lastName","ทดสอบ").param("cardType","PASSPORT").param("officialId","1000000003")))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT first_name FROM guest WHERE guest_id=?",String.class,Integer.parseInt(profile))).isEqualTo("ชื่อเดิม");
        jdbc.update("INSERT INTO booking(booker_name,phone,staff_id,created_at) VALUES ('123','0800000000',?,NOW())",staff);
        int id=jdbc.queryForObject("SELECT MAX(booking_id) FROM booking WHERE staff_id=?",Integer.class,staff);
        jdbc.update("INSERT INTO booking_item(booking_id,room_id,check_in_date,check_out_date,status) VALUES (?,?,'2038-01-10','2038-01-12','BOOKED')",id,Integer.parseInt(room("2401")));
        mvc.perform(auth(post("/bookings/save").param("action","CANCEL").param("bookingId",String.valueOf(id)))).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT booker_name FROM booking WHERE booking_id=?",String.class,id)).isEqualTo("123");
        assertThat(jdbc.queryForObject("SELECT status FROM booking_item WHERE booking_id=?",String.class,id)).isEqualTo("CANCELLED");
    }
    @ParameterizedTest @ValueSource(strings={"สมชาย ใจดี","Anne-Marie O’Brien","José 李","ม.ล. สายใจ"})
    void legitimateUnicodeNamesStillWork(String name)throws Exception {
        mvc.perform(auth(post("/profiles/save").param("recordType","GUEST").param("firstName",name).param("lastName","ทดสอบ").param("cardType","PASSPORT").param("officialId",UUID.randomUUID().toString().replaceAll("[^0-9]",""))))
                .andExpect(status().is3xxRedirection());
    }
}
