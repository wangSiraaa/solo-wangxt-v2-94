package com.example.equity.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 完整时间轴核对：月底授予 + 12 个月悬崖 + 条件批次 + 部分行权 + 取消申请。
 * 每个阶段都独立核对未归属 / 已归属 / 已行权 / 待确认 / 可行权五个数字，
 * 而不是用一个余额数字覆盖所有阶段。
 */
@SpringBootTest
@AutoConfigureMockMvc
class EquityTimelineIntegrationTest {

    @Autowired
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private long registerGrant(String body) throws Exception {
        String resp = mvc.perform(post("/api/grants").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).get("id").asLong();
    }

    private JsonNode quantities(long grantId, String date) throws Exception {
        String resp = mvc.perform(get("/api/grants/" + grantId + "/snapshot").param("date", date))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).get("quantities");
    }

    private void assertQuantities(JsonNode q, long unvested, long vested,
                                  long exercised, long pending, long exercisable) {
        org.assertj.core.api.Assertions.assertThat(q.get("unvested").asLong()).isEqualTo(unvested);
        org.assertj.core.api.Assertions.assertThat(q.get("vested").asLong()).isEqualTo(vested);
        org.assertj.core.api.Assertions.assertThat(q.get("exercised").asLong()).isEqualTo(exercised);
        org.assertj.core.api.Assertions.assertThat(q.get("pending").asLong()).isEqualTo(pending);
        org.assertj.core.api.Assertions.assertThat(q.get("exercisable").asLong()).isEqualTo(exercisable);
        org.assertj.core.api.Assertions.assertThat(q.get("total").asLong())
                .isEqualTo(unvested + vested);
    }

    private long exercise(long grantId, long quantity, String asOf) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("quantity", quantity, "asOf", asOf));
        String resp = mvc.perform(post("/api/grants/" + grantId + "/exercises")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).get("id").asLong();
    }

    @Test
    void monthEndGrantWithCliffConditionalNodePartialExerciseAndCancellation() throws Exception {
        // 2026-01-31（月底）授予 4800 股；48 个月归属；12 个月悬崖；第 12 个月节点为条件批次。
        // 注意月底授予的第 13 个节点落在 2027-02-28，因此行权事件安排在 2/28 之前，
        // 悬崖之后的首批月度归属在最后一个阶段单独核对。
        long grantId = registerGrant("""
                {
                  "name": "ESOP-2026-001",
                  "grantee": "张三",
                  "grantDate": "2026-01-31",
                  "totalShares": 4800,
                  "cliffMonths": 12,
                  "vestingMonths": 48,
                  "conditionalNodes": [
                    {"monthIndex": 12, "conditionLabel": "上市满 6 个月（虚构条件）"}
                  ]
                }""");

        // 阶段 1：悬崖前一天（2027-01-30）——全部未归属，无可行权
        assertQuantities(quantities(grantId, "2027-01-30"), 4800, 0, 0, 0, 0);

        // 阶段 2：悬崖日已到但条件未人工满足（2027-01-31）——仍全部未归属
        assertQuantities(quantities(grantId, "2027-01-31"), 4800, 0, 0, 0, 0);
        mvc.perform(get("/api/grants/" + grantId + "/snapshot").param("date", "2027-01-31"))
                .andExpect(jsonPath("$.nodes[11].conditional").value(true))
                .andExpect(jsonPath("$.nodes[11].conditionMet").value(false))
                .andExpect(jsonPath("$.nodes[11].vestedAsOf").value(false));

        // 阶段 3：人工标记条件满足（2027-02-15）——悬崖 1200 股自 2/15 起归属
        mvc.perform(post("/api/grants/" + grantId + "/nodes/12/satisfy")
                        .param("satisfiedAt", "2027-02-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conditionMet").value(true));

        // 条件满足日之前回看：仍未归属（历史不被后来的标记改写）
        assertQuantities(quantities(grantId, "2027-02-14"), 4800, 0, 0, 0, 0);
        // 满足日当天：悬崖 1200 股归属
        assertQuantities(quantities(grantId, "2027-02-15"), 3600, 1200, 0, 0, 1200);

        // 阶段 4：部分行权 700 股（申请日 2027-02-16），申请待确认 → 占用额度
        long req1 = exercise(grantId, 700, "2027-02-16");
        assertQuantities(quantities(grantId, "2027-02-16"), 3600, 1200, 0, 700, 500);

        // 再申请 600 股（超过剩余可行权 500）→ 409 拒绝，额度不变
        String overBody = json.writeValueAsString(java.util.Map.of("quantity", 600, "asOf", "2027-02-17"));
        mvc.perform(post("/api/grants/" + grantId + "/exercises")
                        .contentType(MediaType.APPLICATION_JSON).content(overBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXCEEDS_EXERCISABLE"))
                .andExpect(jsonPath("$.available").value(500));
        assertQuantities(quantities(grantId, "2027-02-17"), 3600, 1200, 0, 700, 500);

        // 阶段 5：再申请 500 股（2/20）后取消（2/22），取消前后分别核对
        long req2 = exercise(grantId, 500, "2027-02-20");
        assertQuantities(quantities(grantId, "2027-02-20"), 3600, 1200, 0, 1200, 0);
        mvc.perform(post("/api/exercises/" + req2 + "/cancel").param("date", "2027-02-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 取消日前回看：500 股仍处于占用；取消日当天起释放
        assertQuantities(quantities(grantId, "2027-02-21"), 3600, 1200, 0, 1200, 0);
        assertQuantities(quantities(grantId, "2027-02-22"), 3600, 1200, 0, 700, 500);

        // 阶段 6：确认第一笔 700 股（2/24）→ 已行权 700
        mvc.perform(post("/api/exercises/" + req1 + "/confirm").param("date", "2027-02-24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertQuantities(quantities(grantId, "2027-02-23"), 3600, 1200, 0, 700, 500);
        assertQuantities(quantities(grantId, "2027-02-24"), 3600, 1200, 700, 0, 500);

        // 阶段 7：悬崖后月度归属继续。2027-02-28 第 13 节点、3/31 第 14 节点、4/30 第 15 节点各 100
        assertQuantities(quantities(grantId, "2027-02-28"), 3500, 1300, 700, 0, 600);
        assertQuantities(quantities(grantId, "2027-04-30"), 3300, 1500, 700, 0, 800);

        // 已确认 / 已取消的申请再操作都应失败（409）
        mvc.perform(post("/api/exercises/" + req1 + "/cancel")).andExpect(status().isConflict());
        mvc.perform(post("/api/exercises/" + req2 + "/cancel")).andExpect(status().isConflict());
        mvc.perform(post("/api/exercises/" + req1 + "/confirm")).andExpect(status().isConflict());
    }

    @Test
    void plainCliffVestsAutomaticallyAtMonthEnd() throws Exception {
        // 非条件的 12 个月悬崖：悬崖日自动归属 1200 股，无需人工标记
        long grantId = registerGrant("""
                {
                  "name": "ESOP-2026-002",
                  "grantee": "李四",
                  "grantDate": "2026-01-31",
                  "totalShares": 4800,
                  "cliffMonths": 12,
                  "vestingMonths": 48
                }""");

        assertQuantities(quantities(grantId, "2027-01-30"), 4800, 0, 0, 0, 0);
        assertQuantities(quantities(grantId, "2027-01-31"), 3600, 1200, 0, 0, 1200);
        // 2027-02-28：第 13 个节点 +100（月底授予自动落在 2 月最后一天）
        assertQuantities(quantities(grantId, "2027-02-28"), 3500, 1300, 0, 0, 1300);
        // 全部归属完成
        assertQuantities(quantities(grantId, "2030-01-31"), 0, 4800, 0, 0, 4800);
    }

    @Test
    void rejectsExerciseBeyondCliff() throws Exception {
        long grantId = registerGrant("""
                {
                  "name": "ESOP-2026-003",
                  "grantee": "王五",
                  "grantDate": "2026-01-31",
                  "totalShares": 100,
                  "cliffMonths": 12,
                  "vestingMonths": 12
                }""");

        // 悬崖前申请任何数量都应被拒
        String body = json.writeValueAsString(java.util.Map.of("quantity", 1, "asOf", "2027-01-30"));
        mvc.perform(post("/api/grants/" + grantId + "/exercises")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.available").value(0));
    }
}
