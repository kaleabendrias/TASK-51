package com.booking.service;

import com.booking.domain.BlacklistEntry;
import com.booking.domain.User;
import com.booking.mapper.BlacklistMapper;
import com.booking.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BlacklistService {

    private static final Logger log = LoggerFactory.getLogger(BlacklistService.class);
    private static final int DEFAULT_DURATION_DAYS = 7;

    private final BlacklistMapper blacklistMapper;
    private final UserMapper userMapper;

    public BlacklistService(BlacklistMapper blacklistMapper, UserMapper userMapper) {
        this.blacklistMapper = blacklistMapper;
        this.userMapper = userMapper;
    }

    public List<BlacklistEntry> getAll() {
        return blacklistMapper.findAll();
    }

    public BlacklistEntry getById(Long id) {
        return blacklistMapper.findById(id);
    }

    public BlacklistEntry getActiveByUser(Long userId) {
        return blacklistMapper.findActiveByUserId(userId);
    }

    public boolean isBlacklisted(Long userId) {
        BlacklistEntry entry = blacklistMapper.findActiveByUserId(userId);
        return entry != null && !entry.isExpired();
    }

    @Transactional
    public BlacklistEntry blacklist(Long userId, String reason, Integer durationDays, User admin) {
        if (!"ADMINISTRATOR".equals(admin.getRoleName())) {
            throw new SecurityException("Only administrators can blacklist accounts");
        }

        User target = userMapper.findById(userId);
        if (target == null) throw new IllegalArgumentException("User not found");
        if ("ADMINISTRATOR".equals(target.getRoleName())) {
            throw new IllegalArgumentException("Cannot blacklist an administrator");
        }

        // Deactivate any existing active blacklist for this user
        BlacklistEntry existing = blacklistMapper.findActiveByUserId(userId);
        if (existing != null) {
            blacklistMapper.deactivate(existing.getId(), admin.getId(), "Replaced by new blacklist entry");
        }

        int days = (durationDays != null && durationDays > 0) ? durationDays : DEFAULT_DURATION_DAYS;
        LocalDateTime now = LocalDateTime.now();

        BlacklistEntry entry = new BlacklistEntry();
        entry.setUserId(userId);
        entry.setReason(reason);
        entry.setBlacklistedBy(admin.getId());
        entry.setDurationDays(days);
        entry.setStartsAt(now);
        entry.setExpiresAt(now.plusDays(days));
        entry.setActive(true);
        blacklistMapper.insert(entry);

        // Disable the user account
        userMapper.updateEnabled(userId, false);

        log.info("User {} blacklisted by admin {} for {} days: {}", userId, admin.getId(), days, reason);
        return blacklistMapper.findById(entry.getId());
    }

    @Transactional
    public BlacklistEntry lift(Long blacklistId, String liftReason, User admin) {
        if (!"ADMINISTRATOR".equals(admin.getRoleName())) {
            throw new SecurityException("Only administrators can lift blacklists");
        }

        BlacklistEntry entry = blacklistMapper.findById(blacklistId);
        if (entry == null) throw new IllegalArgumentException("Blacklist entry not found");
        if (!entry.getActive()) throw new IllegalStateException("Blacklist entry is already inactive");

        blacklistMapper.deactivate(blacklistId, admin.getId(), liftReason);

        // Re-enable the user
        userMapper.updateEnabled(entry.getUserId(), true);

        log.info("Blacklist {} lifted by admin {}: {}", blacklistId, admin.getId(), liftReason);
        return blacklistMapper.findById(blacklistId);
    }

    @Transactional
    public void autoLiftExpired() {
        List<BlacklistEntry> expired = blacklistMapper.findExpiredActive(LocalDateTime.now());
        for (BlacklistEntry entry : expired) {
            log.info("Auto-lifting expired blacklist {} for user {}", entry.getId(), entry.getUserId());
            blacklistMapper.deactivate(entry.getId(), entry.getBlacklistedBy(), "Auto-lifted: duration expired");
            userMapper.updateEnabled(entry.getUserId(), true);
        }
    }
}
