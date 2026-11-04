package com.trekking.app.features.payment.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.payment.dto.request.RefundRuleTierRequest;
import com.trekking.app.features.payment.dto.request.SaveRefundRulesRequest;
import com.trekking.app.features.payment.dto.response.RefundRulesResponse;
import com.trekking.app.features.payment.entity.RefundRuleSet;
import com.trekking.app.features.payment.repository.RefundRuleSetRepository;
import com.trekking.app.features.payment.service.RefundRulesService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class RefundRulesServiceImpl implements RefundRulesService {
    private final RefundRuleSetRepository rules;
    private final AuthorizationService authorization;
    private final ObjectMapper json;
    private final Clock clock;

    public RefundRulesServiceImpl(RefundRuleSetRepository rules, AuthorizationService authorization,
                                  ObjectMapper json, Clock clock) {
        this.rules = rules;
        this.authorization = authorization;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public RefundRulesResponse current() {
        authorization.require(Permission.POLICY_MANAGE);
        return rules.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(OffsetDateTime.now(clock))
                .map(this::response).orElse(null);
    }

    @Override
    @Transactional
    public RefundRulesResponse save(SaveRefundRulesRequest request) {
        authorization.require(Permission.POLICY_MANAGE);
        var now = OffsetDateTime.now(clock);
        if (request.effectiveFrom().isBefore(now)) throw invalid("effectiveFrom must be in the future");
        var tiers = request.rules().stream().sorted(Comparator.comparingInt(RefundRuleTierRequest::minimumHoursBeforeDeparture)
                .reversed()).toList();
        if (tiers.getLast().minimumHoursBeforeDeparture() != 0
                || tiers.stream().map(RefundRuleTierRequest::minimumHoursBeforeDeparture).distinct().count() != tiers.size()) {
            throw invalid("Rules must have unique thresholds and include a zero-hour fallback");
        }
        var entity = new RefundRuleSet();
        entity.setEffectiveFrom(request.effectiveFrom());
        entity.setCreatedBy(authorization.currentAccountId());
        entity.setRules(json.writeValueAsString(tiers));
        return response(rules.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public String snapshotForBooking(OffsetDateTime at) {
        return rules.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(at)
                .map(RefundRuleSet::getRules).orElse(null);
    }

    private RefundRulesResponse response(RefundRuleSet ruleSet) {
        return new RefundRulesResponse(ruleSet.getId(), ruleSet.getEffectiveFrom(),
                json.readValue(ruleSet.getRules(), json.getTypeFactory().constructCollectionType(List.class, RefundRuleTierRequest.class)));
    }

    private static AuthException invalid(String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "REFUND_RULES_INVALID", message);
    }
}
