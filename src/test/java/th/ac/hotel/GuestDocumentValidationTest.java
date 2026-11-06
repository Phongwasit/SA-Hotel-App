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
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class GuestDocumentValidationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void testDatabaseOnly(){assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("hotel_frontdesk_test");}
    MockHttpServletRequestBuilder staff(MockHttpServletRequestBuilder request){return request.with(authentication(UsernamePasswordAuthenticationToken.authenticated(new StaffPrincipal(1,"reception01","Test Staff"),null,List.of(new SimpleGrantedAuthority("ROLE_STAFF"))))).with(csrf());}
    MockHttpServletRequestBuilder save(String type,String official){return staff(post("/profiles/save").param("recordType","GUEST").param("firstName","ชื่อจริง").param("lastName","ทดสอบ").param("cardType",type).param("officialId",official));}
    int existing(){jdbc.update("INSERT INTO guest(first_name,last_name,card_type,official_id) VALUES ('ชื่อเดิม','ทดสอบ','PASSPORT','0009876')");return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Integer.class);}
    @ParameterizedTest @ValueSource(strings={"NATIONAL_ID","PASSPORT"})
    void bothTypesAcceptDigitsAndRetainLeadingZerosOnCreateAndEdit(String type)throws Exception {
        var created=mvc.perform(save(type,"0001234567890")).andExpect(status().is3xxRedirection()).andReturn();
        String id=created.getResponse().getRedirectedUrl().split("profileId=")[1].split("&")[0];
        assertThat(jdbc.queryForObject("SELECT official_id FROM guest WHERE guest_id=?",String.class,Integer.valueOf(id))).isEqualTo("0001234567890");
        mvc.perform(save(type,"00000042").param("profileId",id)).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForMap("SELECT card_type,official_id FROM guest WHERE guest_id=?",Integer.valueOf(id))).containsEntry("card_type",type).containsEntry("official_id","00000042");
    }
    @ParameterizedTest @ValueSource(strings={"AB123","123a","12 34","12-34","+123","1.5","๑๒๓","１２３"})
    void nonDigitsRejectedForBothTypesOnCreateAndEditWithoutChangingExistingData(String official)throws Exception {
        int id=existing(),count=jdbc.queryForObject("SELECT COUNT(*) FROM guest",Integer.class);
        Map<String,Object> before=jdbc.queryForMap("SELECT * FROM guest WHERE guest_id=?",id);
        for(String type:List.of("NATIONAL_ID","PASSPORT")) {
            mvc.perform(save(type,official)).andExpect(status().isBadRequest()).andExpect(model().attribute("formError","เลขบัตรประชาชนหรือหนังสือเดินทางต้องเป็นตัวเลข 0–9 เท่านั้น"));
            mvc.perform(save(type,official).param("profileId",String.valueOf(id))).andExpect(status().isBadRequest());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guest",Integer.class)).isEqualTo(count);
            assertThat(jdbc.queryForMap("SELECT * FROM guest WHERE guest_id=?",id)).isEqualTo(before);
        }
    }
    @Test void otherOrForgedDocumentTypesCannotBeSaved()throws Exception {
        int id=existing(),count=jdbc.queryForObject("SELECT COUNT(*) FROM guest",Integer.class);
        for(String type:List.of("OTHER","DRIVING_LICENSE","","passport")) {
            mvc.perform(save(type,"123456")).andExpect(status().isBadRequest());
            mvc.perform(save(type,"123456").param("profileId",String.valueOf(id))).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guest",Integer.class)).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT card_type FROM guest WHERE guest_id=?",String.class,id)).isEqualTo("PASSPORT");
    }
    @Test void formHasTwoTypesAndOldOtherRecordNeedsExplicitSelection()throws Exception {
        int id=existing();jdbc.update("UPDATE guest SET card_type='OTHER',official_id='LEGACY-001' WHERE guest_id=?",id);
        mvc.perform(staff(get("/profiles").param("profileId",String.valueOf(id)))).andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"NATIONAL_ID\""))).andExpect(content().string(containsString("value=\"PASSPORT\"")))
                .andExpect(content().string(not(containsString("value=\"OTHER\"")))).andExpect(content().string(containsString("pattern=\"[0-9]+\"")));
        assertThat(jdbc.queryForObject("SELECT official_id FROM guest WHERE guest_id=?",String.class,id)).isEqualTo("LEGACY-001");
    }
}
