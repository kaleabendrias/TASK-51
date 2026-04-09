package com.booking.service;

import com.booking.mapper.ServiceMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PhotoServiceService {

    private final ServiceMapper serviceMapper;

    public PhotoServiceService(ServiceMapper serviceMapper) {
        this.serviceMapper = serviceMapper;
    }

    public com.booking.domain.Service getById(Long id) {
        return serviceMapper.findById(id);
    }

    public List<com.booking.domain.Service> getAll() {
        return serviceMapper.findAll();
    }

    public List<com.booking.domain.Service> getActive() {
        return serviceMapper.findActive();
    }

    public com.booking.domain.Service create(com.booking.domain.Service service) {
        if (service.getName() == null || service.getName().isBlank()) {
            throw new IllegalArgumentException("Service name is required");
        }
        if (service.getPrice() == null || service.getPrice().signum() <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (service.getActive() == null) {
            service.setActive(true);
        }
        serviceMapper.insert(service);
        return serviceMapper.findById(service.getId());
    }

    public com.booking.domain.Service update(com.booking.domain.Service service) {
        com.booking.domain.Service existing = serviceMapper.findById(service.getId());
        if (existing == null) {
            throw new IllegalArgumentException("Service not found");
        }
        serviceMapper.update(service);
        return serviceMapper.findById(service.getId());
    }
}
