package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SearchFilterApiIT extends BaseApiIT {

    @Test void searchNoFiltersReturnsPaginatedResult() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/search").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(3)))
            .andExpect(jsonPath("$.page").value(1));
    }

    @Test void searchByKeyword() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/search?keyword=Portrait").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].title", containsString("Portrait")));
    }

    @Test void searchByCategory() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/search?category=FAMILY").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.items[*].category", everyItem(equalTo("FAMILY"))));
    }

    @Test void searchByPriceRange() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/search?minPrice=200&maxPrice=300").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items[*].price", everyItem(
                allOf(greaterThanOrEqualTo(200.0), lessThanOrEqualTo(300.0)))));
    }

    @Test void searchByLocation() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/search?location=Downtown").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].location", containsString("Downtown")));
    }

    @Test void searchCombinedFilters() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/search?category=PORTRAIT&minPrice=100&maxPrice=200").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.items[0].category").value("PORTRAIT"));
    }

    @Test void searchNoResults() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/search?keyword=nonexistent_xyz").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0))
            .andExpect(jsonPath("$.total").value(0));
    }

    @Test void searchPagination() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/search?page=1&size=1").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.size").value(1))
            .andExpect(jsonPath("$.totalPages").value(greaterThanOrEqualTo(1)));
    }

    @Test void availableTimeSlotsReturnsOnlyAvailable() throws Exception {
        // Create a guaranteed-available slot so the test is independent of prior bookings.
        MockHttpSession photo = loginAs("photo1");
        MvcResult slotR = mvc.perform(post("/api/timeslots").session(photo)
                .header("Origin", TEST_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("listingId", 1, "slotDate", "2027-08-10",
                        "startTime", "09:00", "endTime", "10:00", "capacity", 5))))
            .andExpect(status().isOk()).andReturn();

        MockHttpSession s = loginAs("cust1");
        MvcResult result = mvc.perform(get("/api/timeslots/listing/1/available?start=2027-08-01&end=2027-08-31").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(greaterThan(0)))
            .andReturn();

        LocalDate today = LocalDate.now();
        LocalDate rangeStart = LocalDate.of(2027, 8, 1);
        LocalDate rangeEnd = LocalDate.of(2027, 8, 31);

        // Every returned slot must belong to listing 1, have remaining capacity,
        // and fall within the requested date range (not expired).
        List<?> slots = om.readValue(result.getResponse().getContentAsString(), List.class);
        for (Object entry : slots) {
            @SuppressWarnings("unchecked")
            Map<String, Object> slot = (Map<String, Object>) entry;
            assertEquals(1, ((Number) slot.get("listingId")).intValue(),
                "All slots must belong to listing 1");
            int booked = ((Number) slot.get("bookedCount")).intValue();
            int capacity = ((Number) slot.get("capacity")).intValue();
            assertTrue(booked < capacity,
                "Available endpoint must only return slots with remaining capacity, " +
                "but got bookedCount=" + booked + " capacity=" + capacity);
            LocalDate slotDate = LocalDate.parse(slot.get("slotDate").toString());
            assertFalse(slotDate.isBefore(today),
                "Available endpoint must not return expired (past) slots, but got " + slotDate);
            assertFalse(slotDate.isBefore(rangeStart) || slotDate.isAfter(rangeEnd),
                "Slot date " + slotDate + " is outside the requested range");
        }
    }

    @Test void listingDetailReturnsPhotographerName() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/1").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photographerName").isNotEmpty())
            .andExpect(jsonPath("$.price").isNumber());
    }

    @Test void listingNotFound() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/9999").session(s))
            .andExpect(status().isNotFound());
    }

    // ---- Server-side search suggestions ----

    @Test void searchRecordsTermAndSuggestsIt() throws Exception {
        MockHttpSession s = loginAs("cust1");
        // Search twice to bump frequency
        mvc.perform(get("/api/listings/search?keyword=wedding").session(s))
            .andExpect(status().isOk());
        mvc.perform(get("/api/listings/search?keyword=wedding").session(s))
            .andExpect(status().isOk());

        // Suggestions should include the recorded term
        mvc.perform(get("/api/listings/search/suggestions?limit=10").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasItem("wedding")));
    }

    @Test void searchSuggestionsDefaultLimit() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/search/suggestions").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test void searchBlankKeywordDoesNotRecordTerm() throws Exception {
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/listings/search?keyword=").session(s))
            .andExpect(status().isOk());
    }
}
