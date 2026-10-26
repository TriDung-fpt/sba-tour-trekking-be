package com.trekking.app.features.payment.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.payment.dto.response.AdminPaymentAttemptResponse;
import com.trekking.app.features.payment.dto.response.AdminPaymentResponse;
import com.trekking.app.features.payment.dto.response.AdminRefundResponse;
import com.trekking.app.features.payment.entity.CustomerPayment;
import com.trekking.app.features.payment.entity.CustomerRefund;
import com.trekking.app.features.payment.entity.PaymentAttempt;
import com.trekking.app.features.payment.enums.AllocationStatus;
import com.trekking.app.features.payment.enums.PaymentStatus;
import com.trekking.app.features.payment.repository.CustomerPaymentRepository;
import com.trekking.app.features.payment.repository.CustomerRefundRepository;
import com.trekking.app.features.payment.repository.PaymentAttemptRepository;
import com.trekking.app.features.payment.service.AdminPaymentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminPaymentServiceImpl implements AdminPaymentService {
    private final PaymentAttemptRepository attempts;
    private final CustomerPaymentRepository payments;
    private final CustomerRefundRepository refunds;
    private final AuthorizationService authorization;

    public AdminPaymentServiceImpl(PaymentAttemptRepository attempts, CustomerPaymentRepository payments,
                                   CustomerRefundRepository refunds, AuthorizationService authorization) {
        this.attempts = attempts;
        this.payments = payments;
        this.refunds = refunds;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public Page<AdminPaymentAttemptResponse> listAttempts(String status, Long bookingId, int page, int size) {
        authorization.require(Permission.FINANCE_READ);
        return attempts.search(parseAttemptStatus(status), bookingId, pageable(page, size))
                .map(AdminPaymentServiceImpl::attemptResponse);
    }

    @Transactional(readOnly = true)
    public Page<AdminPaymentResponse> listPayments(String allocation, Long bookingId, int page, int size) {
        authorization.require(Permission.FINANCE_READ);
        return payments.search(parseAllocation(allocation), bookingId, pageable(page, size))
                .map(AdminPaymentServiceImpl::paymentResponse);
    }

    @Transactional(readOnly = true)
    public Page<AdminRefundResponse> listRefunds(Long bookingId, int page, int size) {
        authorization.require(Permission.FINANCE_READ);
        return refunds.search(bookingId, pageable(page, size)).map(AdminPaymentServiceImpl::refundResponse);
    }

    private static PageRequest pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION",
                    "page must be >= 0 and size must be between 1 and 100");
        }
        return PageRequest.of(page, size);
    }

    private static AdminPaymentAttemptResponse attemptResponse(PaymentAttempt attempt) {
        return new AdminPaymentAttemptResponse(attempt.getId(), attempt.getBookingId(),
                attempt.getProvider().getValue(), attempt.getMerchantReference(),
                attempt.getPurpose().getValue(), attempt.getRequestedAmountVnd(),
                attempt.getStatus().getValue(), attempt.getExpiresAt(), attempt.getCreatedAt());
    }

    private static AdminPaymentResponse paymentResponse(CustomerPayment payment) {
        return new AdminPaymentResponse(payment.getId(), payment.getBookingId(), payment.getAttemptId(),
                payment.getProvider().getValue(), payment.getTransactionReference(),
                payment.getAmountVnd(), payment.getGatewayFeeVnd(), payment.getAllocationStatus().getValue(),
                payment.getReceivedAt(), payment.getVerifiedBy());
    }

    private static AdminRefundResponse refundResponse(CustomerRefund refund) {
        return new AdminRefundResponse(refund.getId(), refund.getRefundRequestId(), refund.getBookingId(),
                refund.getOriginalPaymentId(), refund.getAmountVnd(), refund.getChannel().getValue(),
                refund.getTransactionReference(), refund.getRefundedAt(), refund.getRecordedBy());
    }

    private static PaymentStatus parseAttemptStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (PaymentStatus status : PaymentStatus.values()) {
            if (status.name().equalsIgnoreCase(raw) || status.getValue().equalsIgnoreCase(raw)) return status;
        }
        throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid status filter");
    }

    private static AllocationStatus parseAllocation(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (AllocationStatus status : AllocationStatus.values()) {
            if (status.name().equalsIgnoreCase(raw) || status.getValue().equalsIgnoreCase(raw)) return status;
        }
        throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid allocation filter");
    }
}
