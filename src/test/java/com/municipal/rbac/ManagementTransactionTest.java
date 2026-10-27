package com.municipal.rbac;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

// No test transaction: verify the controller's real transaction rolls back before the next request.
@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class ManagementTransactionTest {
  @Autowired JdbcTemplate db; @Autowired MockMvc mvc; @Autowired ObjectMapper json;
  @Test void failedLastAdministratorReplacementRollsBackAllGrantsAndAudit()throws Exception{
    String token="Bearer "+json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"account\":\"admin\",\"password\":\"admin123\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data").get("token").asText();
    long id=db.queryForObject("SELECT id FROM users WHERE username='admin'",Long.class);
    var before=db.queryForList("SELECT * FROM management_scopes WHERE user_id=? ORDER BY id",id);
    int audit=db.queryForObject("SELECT count(*) FROM authorization_changes",Integer.class);
    try{
      mvc.perform(put("/api/users/"+id+"/role-assignments").header("Authorization",token).contentType("application/json").content("{\"roleIds\":[],\"scopes\":[]}")).andExpect(status().isBadRequest());
      assertEquals(before,db.queryForList("SELECT * FROM management_scopes WHERE user_id=? ORDER BY id",id));
      assertEquals(audit,db.queryForObject("SELECT count(*) FROM authorization_changes",Integer.class));
      mvc.perform(get("/api/management/permissions").header("Authorization",token)).andExpect(status().isOk());
    }finally{mvc.perform(post("/api/auth/logout").header("Authorization",token)).andExpect(status().isOk());}
  }
}
