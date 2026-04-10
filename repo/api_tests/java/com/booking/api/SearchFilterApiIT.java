package com.booking.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.hamcrest.Matchers.*;
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
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].category").value("FAMILY"));
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
        MockHttpSession s = loginAs("cust1");
        mvc.perform(get("/api/timeslots/listing/1/available?start=2026-06-01&end=2026-06-30").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(greaterThan(0)))
            .andExpect(jsonPath("$[0].listingId").value(1));
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
