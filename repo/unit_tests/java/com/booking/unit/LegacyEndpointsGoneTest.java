package com.booking.unit;

import com.booking.controller.AttachmentController;
import com.booking.controller.BookingController;
import com.booking.controller.ServiceController;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEndpointsGoneTest {

    // ---- BookingController ----

    private final BookingController bookingCtrl = new BookingController();

    @Test void bookingListGone() {
        ResponseEntity<?> r = bookingCtrl.list();
        assertEquals(410, r.getStatusCode().value());
        assertTrue(((Map<?,?>) r.getBody()).get("error").toString().contains("/api/orders"));
    }

    @Test void bookingGetGone() { assertEquals(410, bookingCtrl.get(1L).getStatusCode().value()); }
    @Test void bookingCreateGone() { assertEquals(410, bookingCtrl.create().getStatusCode().value()); }
    @Test void bookingUpdateGone() { assertEquals(410, bookingCtrl.update(1L).getStatusCode().value()); }
    @Test void bookingStatusGone() { assertEquals(410, bookingCtrl.updateStatus(1L).getStatusCode().value()); }

    // ---- ServiceController ----

    private final ServiceController serviceCtrl = new ServiceController();

    @Test void serviceListGone() {
        ResponseEntity<?> r = serviceCtrl.list();
        assertEquals(410, r.getStatusCode().value());
        assertTrue(((Map<?,?>) r.getBody()).get("error").toString().contains("/api/listings"));
    }

    @Test void serviceListAllGone() { assertEquals(410, serviceCtrl.listAll().getStatusCode().value()); }
    @Test void serviceGetGone() { assertEquals(410, serviceCtrl.get(1L).getStatusCode().value()); }
    @Test void serviceCreateGone() { assertEquals(410, serviceCtrl.create().getStatusCode().value()); }
    @Test void serviceUpdateGone() { assertEquals(410, serviceCtrl.update(1L).getStatusCode().value()); }

    // ---- AttachmentController ----

    private final AttachmentController attachCtrl = new AttachmentController();

    @Test void attachmentListGone() {
        ResponseEntity<?> r = attachCtrl.listForBooking(1L);
        assertEquals(410, r.getStatusCode().value());
        assertTrue(((Map<?,?>) r.getBody()).get("error").toString().contains("/api/messages"));
    }

    @Test void attachmentUploadGone() { assertEquals(410, attachCtrl.upload(1L).getStatusCode().value()); }
    @Test void attachmentDownloadGone() { assertEquals(410, attachCtrl.download(1L).getStatusCode().value()); }
    @Test void attachmentDeleteGone() { assertEquals(410, attachCtrl.delete(1L).getStatusCode().value()); }
}
