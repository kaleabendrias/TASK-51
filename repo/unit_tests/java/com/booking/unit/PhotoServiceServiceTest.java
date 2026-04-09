package com.booking.unit;

import com.booking.domain.Service;
import com.booking.mapper.ServiceMapper;
import com.booking.service.PhotoServiceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhotoServiceServiceTest {

    @Mock ServiceMapper serviceMapper;
    @InjectMocks PhotoServiceService svc;

    @Test void createSuccess() {
        Service s = new Service(); s.setName("Test"); s.setPrice(BigDecimal.valueOf(50));
        when(serviceMapper.findById(any())).thenReturn(s);
        svc.create(s);
        verify(serviceMapper).insert(argThat(sv -> sv.getActive()));
    }

    @Test void createNoNameFails() {
        Service s = new Service(); s.setPrice(BigDecimal.TEN);
        assertThrows(IllegalArgumentException.class, () -> svc.create(s));
    }

    @Test void createBlankNameFails() {
        Service s = new Service(); s.setName("  "); s.setPrice(BigDecimal.TEN);
        assertThrows(IllegalArgumentException.class, () -> svc.create(s));
    }

    @Test void createZeroPriceFails() {
        Service s = new Service(); s.setName("X"); s.setPrice(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> svc.create(s));
    }

    @Test void createNullPriceFails() {
        Service s = new Service(); s.setName("X");
        assertThrows(IllegalArgumentException.class, () -> svc.create(s));
    }

    @Test void updateNotFound() {
        when(serviceMapper.findById(99L)).thenReturn(null);
        Service s = new Service(); s.setId(99L);
        assertThrows(IllegalArgumentException.class, () -> svc.update(s));
    }

    @Test void updateSuccess() {
        Service existing = new Service(); existing.setId(1L);
        when(serviceMapper.findById(1L)).thenReturn(existing);
        svc.update(existing);
        verify(serviceMapper).update(any());
    }

    @Test void delegateMethods() {
        when(serviceMapper.findAll()).thenReturn(List.of());
        when(serviceMapper.findActive()).thenReturn(List.of());
        when(serviceMapper.findById(1L)).thenReturn(new Service());
        svc.getAll(); svc.getActive(); svc.getById(1L);
        verify(serviceMapper).findAll();
        verify(serviceMapper).findActive();
    }
}
