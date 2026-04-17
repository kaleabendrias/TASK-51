package com.booking.unit;

import com.booking.controller.ListingController;
import com.booking.domain.Listing;
import com.booking.domain.SearchTerm;
import com.booking.domain.User;
import com.booking.service.ListingService;
import com.booking.service.SearchTermService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingControllerTest {

    @Mock ListingService listingService;
    @Mock SearchTermService searchTermService;
    @InjectMocks ListingController controller;

    private MockHttpSession photographerSession;
    private MockHttpSession customerSession;
    private MockHttpSession adminSession;

    @BeforeEach
    void setUp() {
        User photographer = new User(); photographer.setId(2L); photographer.setRoleName("PHOTOGRAPHER");
        photographerSession = new MockHttpSession();
        photographerSession.setAttribute("currentUser", photographer);

        User customer = new User(); customer.setId(4L); customer.setRoleName("CUSTOMER");
        customerSession = new MockHttpSession();
        customerSession.setAttribute("currentUser", customer);

        User admin = new User(); admin.setId(1L); admin.setRoleName("ADMINISTRATOR");
        adminSession = new MockHttpSession();
        adminSession.setAttribute("currentUser", admin);
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_delegatesToServiceAndReturns200() {
        when(listingService.getActive()).thenReturn(List.of());

        ResponseEntity<?> resp = controller.list();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(listingService).getActive();
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @Test
    void get_existingListing_returns200() {
        Listing listing = new Listing(); listing.setId(5L);
        when(listingService.getById(5L)).thenReturn(listing);

        ResponseEntity<?> resp = controller.get(5L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(listing, resp.getBody());
    }

    @Test
    void get_nonExistentListing_returns404() {
        when(listingService.getById(999L)).thenReturn(null);

        ResponseEntity<?> resp = controller.get(999L);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    // ── myListings ────────────────────────────────────────────────────────────

    @Test
    void myListings_photographer_delegatesToService() {
        when(listingService.getByPhotographer(2L)).thenReturn(List.of());

        ResponseEntity<?> resp = controller.myListings(photographerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(listingService).getByPhotographer(2L);
    }

    @Test
    void myListings_admin_delegatesToService() {
        when(listingService.getByPhotographer(1L)).thenReturn(List.of());

        ResponseEntity<?> resp = controller.myListings(adminSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void myListings_customer_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> controller.myListings(customerSession),
                "CUSTOMER role must not access myListings");
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_photographerUser_delegatesToService() {
        Listing input = new Listing(); input.setTitle("Studio");
        Listing saved = new Listing(); saved.setId(10L);
        when(listingService.create(any(), any())).thenReturn(saved);

        ResponseEntity<?> resp = controller.create(input, photographerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(saved, resp.getBody());
    }

    @Test
    void create_securityException_returns403() {
        when(listingService.create(any(), any()))
                .thenThrow(new SecurityException("Not a photographer"));

        ResponseEntity<?> resp = controller.create(new Listing(), customerSession);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertNotNull(body.get("error"));
    }

    @Test
    void create_illegalArgument_returns400() {
        when(listingService.create(any(), any()))
                .thenThrow(new IllegalArgumentException("Missing required field"));

        ResponseEntity<?> resp = controller.create(new Listing(), photographerSession);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertTrue(body.get("error").toString().contains("Missing required field"));
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_delegatesToService() {
        Listing updated = new Listing(); updated.setId(5L);
        when(listingService.update(any(), any())).thenReturn(updated);

        ResponseEntity<?> resp = controller.update(5L, new Listing(), photographerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void update_securityException_returns403() {
        when(listingService.update(any(), any()))
                .thenThrow(new SecurityException("Not your listing"));

        ResponseEntity<?> resp = controller.update(5L, new Listing(), customerSession);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void update_illegalArgument_returns400() {
        when(listingService.update(any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid price"));

        ResponseEntity<?> resp = controller.update(5L, new Listing(), photographerSession);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }
}
