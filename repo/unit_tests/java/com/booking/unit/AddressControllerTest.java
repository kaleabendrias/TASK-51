package com.booking.unit;

import com.booking.controller.AddressController;
import com.booking.domain.Address;
import com.booking.domain.User;
import com.booking.service.AddressService;
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
import static org.mockito.Mockito.*;

/**
 * Unit tests for AddressController — directly instantiates the controller with
 * a mocked AddressService and a MockHttpSession carrying a synthetic user,
 * then asserts on the returned ResponseEntity without loading the full
 * Spring context.
 */
@ExtendWith(MockitoExtension.class)
class AddressControllerTest {

    @Mock AddressService addressService;
    @InjectMocks AddressController controller;

    private MockHttpSession customerSession;
    private MockHttpSession adminSession;
    private MockHttpSession otherCustomerSession;

    @BeforeEach
    void setUp() {
        User customer = new User(); customer.setId(4L); customer.setRoleName("CUSTOMER");
        customerSession = new MockHttpSession();
        customerSession.setAttribute("currentUser", customer);

        User admin = new User(); admin.setId(1L); admin.setRoleName("ADMINISTRATOR");
        adminSession = new MockHttpSession();
        adminSession.setAttribute("currentUser", admin);

        User other = new User(); other.setId(10L); other.setRoleName("CUSTOMER");
        otherCustomerSession = new MockHttpSession();
        otherCustomerSession.setAttribute("currentUser", other);
    }

    // ── GET /{id} ──────────────────────────────────────────────────────────────

    @Test
    void getById_notFound_returns404() {
        when(addressService.getById(99L)).thenReturn(null);

        ResponseEntity<?> resp = controller.get(99L, customerSession);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode(),
                "Missing address must return 404");
    }

    @Test
    void getById_wrongOwner_returns403() {
        Address addr = new Address();
        addr.setId(1L);
        addr.setUserId(4L);   // owned by userId 4
        when(addressService.getById(1L)).thenReturn(addr);

        // userId=10 is NOT the owner and NOT an admin
        ResponseEntity<?> resp = controller.get(1L, otherCustomerSession);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                "Non-owner customer must get 403 when fetching another user's address");
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertNotNull(body.get("error"), "403 response must include an error message");
    }

    @Test
    void getById_owningCustomer_returns200() {
        Address addr = new Address();
        addr.setId(1L);
        addr.setUserId(4L);   // customerSession is userId=4
        when(addressService.getById(1L)).thenReturn(addr);

        ResponseEntity<?> resp = controller.get(1L, customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode(),
                "Address owner must be able to retrieve their own address");
    }

    @Test
    void getById_admin_canAccessAnyAddress() {
        Address addr = new Address();
        addr.setId(1L);
        addr.setUserId(4L);   // owned by customer, not admin
        when(addressService.getById(1L)).thenReturn(addr);

        // Admin session (userId=1) is not the owner but should still get 200
        ResponseEntity<?> resp = controller.get(1L, adminSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode(),
                "ADMINISTRATOR must be able to read any address regardless of ownership");
    }

    // ── POST / (create) ────────────────────────────────────────────────────────

    @Test
    void create_delegatesToServiceAndReturns200() {
        Address input = new Address(); input.setLabel("Home"); input.setStreet("1 Main");
        Address saved = new Address(); saved.setId(42L); saved.setLabel("Home");
        when(addressService.create(any(), any())).thenReturn(saved);

        ResponseEntity<?> resp = controller.create(input, customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(saved, resp.getBody(), "Controller must return the address returned by service");
    }

    @Test
    void create_illegalArgument_returns400() {
        when(addressService.create(any(), any()))
                .thenThrow(new IllegalArgumentException("ZIP/state mismatch"));

        ResponseEntity<?> resp = controller.create(new Address(), customerSession);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertTrue(body.get("error").toString().contains("ZIP/state mismatch"),
                "400 body must echo the exception message");
    }

    // ── PUT /{id} (update) ─────────────────────────────────────────────────────

    @Test
    void update_securityException_returns403() {
        when(addressService.update(any(), any()))
                .thenThrow(new SecurityException("Not your address"));

        Address upd = new Address();
        ResponseEntity<?> resp = controller.update(1L, upd, customerSession);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertNotNull(body.get("error"), "403 body must contain an error key");
    }

    @Test
    void update_illegalArgument_returns400() {
        when(addressService.update(any(), any()))
                .thenThrow(new IllegalArgumentException("Not found"));

        ResponseEntity<?> resp = controller.update(1L, new Address(), customerSession);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    // ── DELETE /{id} ───────────────────────────────────────────────────────────

    @Test
    void delete_nonOwner_returns403() {
        doThrow(new SecurityException("Not your address"))
                .when(addressService).delete(eq(1L), any());

        ResponseEntity<?> resp = controller.delete(1L, customerSession);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void delete_owner_returns200WithMessage() {
        doNothing().when(addressService).delete(eq(5L), any());

        ResponseEntity<?> resp = controller.delete(5L, customerSession);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, ?> body = (Map<String, ?>) resp.getBody();
        assertNotNull(body.get("message"), "Successful delete must return a message field");
    }
}
