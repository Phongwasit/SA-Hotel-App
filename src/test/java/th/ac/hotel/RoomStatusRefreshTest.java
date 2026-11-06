package th.ac.hotel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import th.ac.hotel.model.Room;
import th.ac.hotel.security.StaffPrincipal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class RoomStatusRefreshTest {
    @Autowired MockMvc mvc;
    @MockitoSpyBean JdbcTemplate jdbc;
    MockHttpServletRequestBuilder staff(MockHttpServletRequestBuilder request) {
        return request.with(authentication(UsernamePasswordAuthenticationToken.authenticated(new StaffPrincipal(1,"reception01","Test Staff"),null,List.of(new SimpleGrantedAuthority("ROLE_STAFF")))));
    }
    @Test void snapshotRequiresSelectedStaff()throws Exception {
        mvc.perform(get("/room-status/data")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("http://localhost/login"));
    }
    @Test void snapshotReadsFreshRoomStateAndExcludesUnneededData()throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("hotel_frontdesk_test");
        jdbc.update("UPDATE room SET cleanliness='CLEAN',usability='USABLE',condition_reason=NULL WHERE room_number='2608'");
        mvc.perform(staff(get("/room-status/data"))).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$",hasSize(41))).andExpect(jsonPath("$[?(@.roomNumber=='2608')].cleanliness",contains("CLEAN")))
                .andExpect(jsonPath("$[0].blockingIntervals").doesNotExist()).andExpect(jsonPath("$[0].standardNightlyRate").doesNotExist());
        jdbc.update("UPDATE room SET cleanliness='DIRTY',condition_reason='Fresh snapshot test' WHERE room_number='2608'");
        mvc.perform(staff(get("/room-status/data"))).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.roomNumber=='2608')].cleanliness",contains("DIRTY")))
                .andExpect(jsonPath("$[?(@.roomNumber=='2608')].conditionReason",contains("Fresh snapshot test")));
    }
    @Test void databaseFailureDoesNotReturnSuccessfulEmptyRoomList()throws Exception {
        doThrow(new CannotGetJdbcConnectionException("offline fixture")).when(jdbc).query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<Room>>any());
        mvc.perform(staff(get("/room-status/data"))).andExpect(status().isServiceUnavailable());
    }
}
