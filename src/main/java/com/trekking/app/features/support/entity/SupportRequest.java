package com.trekking.app.features.support.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.company.entity.Company;
import com.trekking.app.features.payment.entity.CustomerPayment;
import com.trekking.app.features.payment.entity.RefundRequest;
import com.trekking.app.features.statement.entity.CompanyStatement;
import com.trekking.app.features.support.enums.SupportCategory;
import com.trekking.app.features.support.enums.SupportStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "support_requests", schema = "trekking")
public class SupportRequest extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "requester_company_id")
    private Long requesterCompanyId;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "statement_id")
    private Long statementId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "refund_request_id")
    private Long refundRequestId;

    @Column(name = "subject", nullable = false, length = 250)
    private String subject;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, columnDefinition = "text")
    private SupportCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private SupportStatus status = SupportStatus.NEW;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "requester_company_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Company requesterCompany;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "booking_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "statement_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private CompanyStatement statement;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "payment_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private CustomerPayment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "refund_request_id", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private RefundRequest refundRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "assigned_to", referencedColumnName = "id", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private AppAccount assignedToAccount;
}
