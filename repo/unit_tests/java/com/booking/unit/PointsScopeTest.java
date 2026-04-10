package com.booking.unit;

import com.booking.domain.PointsRule;
import com.booking.domain.User;
import com.booking.mapper.PointsLedgerMapper;
import com.booking.mapper.PointsRuleMapper;
import com.booking.mapper.UserMapper;
import com.booking.service.PointsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointsScopeTest {

    @Mock PointsLedgerMapper ledgerMapper;
    @Mock PointsRuleMapper ruleMapper;
    @Mock UserMapper userMapper;
    @InjectMocks PointsService pointsService;

    private PointsRule rule(String scope, int pts) {
        PointsRule r = new PointsRule(); r.setName("TEST"); r.setPoints(pts);
        r.setScope(scope); r.setActive(true); r.setDescription("Test");
        r.setTriggerEvent("TEST_EVENT"); return r;
    }
    private User user(Long id, String dept, String team) {
        User u = new User(); u.setId(id); u.setDepartment(dept); u.setTeam(team); return u;
    }

    @Test void individualScopeAwardsSingleUser() {
        when(ruleMapper.findByTrigger("EVT")).thenReturn(rule("INDIVIDUAL", 10));
        when(ledgerMapper.getBalance(1L)).thenReturn(10);
        pointsService.awardByTrigger("EVT", 1L, "REF", 1L, "ctx");
        verify(ledgerMapper, times(1)).insert(any());
        verify(ledgerMapper).adjustUserBalanceAtomic(1L, 10);
    }

    @Test void departmentScopeAwardsAllInDept() {
        when(ruleMapper.findByTrigger("EVT")).thenReturn(rule("DEPARTMENT", 5));
        User trigger = user(1L, "SALES", null);
        User peer = user(2L, "SALES", null);
        when(userMapper.findById(1L)).thenReturn(trigger);
        when(userMapper.findByDepartment("SALES")).thenReturn(List.of(trigger, peer));
        when(ledgerMapper.getBalance(anyLong())).thenReturn(0);
        pointsService.awardByTrigger("EVT", 1L, "REF", 1L, "ctx");
        verify(ledgerMapper, times(2)).insert(any());
    }

    @Test void teamScopeAwardsAllInTeam() {
        when(ruleMapper.findByTrigger("EVT")).thenReturn(rule("TEAM", 15));
        User trigger = user(1L, null, "ALPHA");
        User peer = user(3L, null, "ALPHA");
        when(userMapper.findById(1L)).thenReturn(trigger);
        when(userMapper.findByTeam("ALPHA")).thenReturn(List.of(trigger, peer));
        when(ledgerMapper.getBalance(anyLong())).thenReturn(0);
        pointsService.awardByTrigger("EVT", 1L, "REF", 1L, "ctx");
        verify(ledgerMapper, times(2)).insert(any());
    }

    @Test void nullDepartmentFallsBackToIndividual() {
        when(ruleMapper.findByTrigger("EVT")).thenReturn(rule("DEPARTMENT", 5));
        when(userMapper.findById(1L)).thenReturn(user(1L, null, null));
        when(ledgerMapper.getBalance(1L)).thenReturn(0);
        pointsService.awardByTrigger("EVT", 1L, "REF", 1L, "ctx");
        verify(ledgerMapper, times(1)).insert(any());
    }

    @Test void classScopeAwardsAllInSameRole() {
        when(ruleMapper.findByTrigger("EVT")).thenReturn(rule("CLASS", 8));
        User trigger = new User(); trigger.setId(4L); trigger.setRoleId(1L); trigger.setRoleName("CUSTOMER");
        User peer = new User(); peer.setId(5L); peer.setRoleId(1L);
        when(userMapper.findById(4L)).thenReturn(trigger);
        when(userMapper.findByRoleId(1L)).thenReturn(List.of(trigger, peer));
        when(ledgerMapper.getBalance(anyLong())).thenReturn(0);
        pointsService.awardByTrigger("EVT", 4L, "REF", 1L, "ctx");
        verify(ledgerMapper, times(2)).insert(any());
    }

    @Test void inactiveRuleSkipsAward() {
        PointsRule r = rule("INDIVIDUAL", 10); r.setActive(false);
        when(ruleMapper.findByTrigger("EVT")).thenReturn(r);
        pointsService.awardByTrigger("EVT", 1L, "REF", 1L, "ctx");
        verify(ledgerMapper, never()).insert(any());
    }

    @Test void noRuleSkipsAward() {
        when(ruleMapper.findByTrigger("MISSING")).thenReturn(null);
        pointsService.awardByTrigger("MISSING", 1L, "REF", 1L, "ctx");
        verify(ledgerMapper, never()).insert(any());
    }
}
