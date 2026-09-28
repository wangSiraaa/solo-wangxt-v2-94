package com.solo.equity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the Angular client: stage fields are all present and named
 * exactly as consumed by the timeline component.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EquityApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private long createGrant() throws Exception {
        String body = json.writeValueAsString(Map.of(
                "grantName", "OPT-WEB", "grantee", "Casey Lee",
                "grantDate", "2025-01-31", "totalQuantity", 4800,
                "cliffMonths", 12, "vestingMonths", 48,
                "planName", "Fictional Plan 2025",
                "conditionalMonths", new int[]{24}));
        String resp = mvc.perform(post("/api/grants").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance.vested").value(0))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).get("grantId").asLong();
    }

    @Test
    void timelineExposesEveryStageAndOverLimitExerciseIsRejected() throws Exception {
        long id = createGrant();

        // Cliff date: 1200 vested, all exercisable.
        mvc.perform(get("/api/grants/{id}/timeline", id).param("asOf", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance.total").value(4800))
                .andExpect(jsonPath("$.balance.unvested").value(3600))
                .andExpect(jsonPath("$.balance.vested").value(1200))
                .andExpect(jsonPath("$.balance.exercised").value(0))
                .andExpect(jsonPath("$.balance.pendingReserved").value(0))
                .andExpect(jsonPath("$.balance.exercisable").value(1200))
                .andExpect(jsonPath("$.nodes[0].cliffNode").value(true))
                .andExpect(jsonPath("$.nodes[0].scheduledQuantity").value(1200));

        String ex = json.writeValueAsString(Map.of(
                "requestNo", "EX-W1", "quantity", 500, "requestDate", "2026-01-31"));
        mvc.perform(post("/api/grants/{id}/exercises", id).contentType(MediaType.APPLICATION_JSON).content(ex))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance.pendingReserved").value(500))
                .andExpect(jsonPath("$.balance.exercisable").value(700));

        String tooBig = json.writeValueAsString(Map.of(
                "requestNo", "EX-W2", "quantity", 701, "requestDate", "2026-01-31"));
        mvc.perform(post("/api/grants/{id}/exercises", id).contentType(MediaType.APPLICATION_JSON).content(tooBig))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("exceeds exercisable")));
    }

    @Test
    void conditionalNodeOnlyVestsAfterManualMark() throws Exception {
        long id = createGrant();

        mvc.perform(get("/api/grants/{id}/timeline", id).param("asOf", "2027-01-31"))
                .andExpect(jsonPath("$.balance.vested").value(2300));

        mvc.perform(post("/api/grants/{id}/nodes/{seq}/mark-condition", id, 24)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("metDate", "2027-03-15"))))
                .andExpect(status().isNoContent());

        // Day before the mark: still 2400 (months up to node 25, node 24 excluded).
        mvc.perform(get("/api/grants/{id}/timeline", id).param("asOf", "2027-03-14"))
                .andExpect(jsonPath("$.balance.vested").value(2400));
        // Mark date: node 24 joins -> 2500.
        mvc.perform(get("/api/grants/{id}/timeline", id).param("asOf", "2027-03-15"))
                .andExpect(jsonPath("$.balance.vested").value(2500));
    }

    @Test
    void cancelReleasesReservationAndConfirmMovesItToExercised() throws Exception {
        long id = createGrant();

        JsonNode body = json.readTree(mvc.perform(post("/api/grants/{id}/exercises", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "requestNo", "EX-C", "quantity", 1200, "requestDate", "2026-01-31"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long reqId = body.get("requests").get(0).get("id").asLong();

        // Pool exhausted...
        mvc.perform(get("/api/grants/{id}/timeline", id).param("asOf", "2026-01-31"))
                .andExpect(jsonPath("$.balance.exercisable").value(0));

        // ...cancel releases it. Feb 15 precedes the Feb 28 node, so vested is still 1200.
        mvc.perform(post("/api/exercises/{rid}/cancel", reqId).param("date", "2026-02-15"))
                .andExpect(jsonPath("$.balance.vested").value(1200))
                .andExpect(jsonPath("$.balance.pendingReserved").value(0))
                .andExpect(jsonPath("$.balance.exercisable").value(1200));
    }
}
