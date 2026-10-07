package com.trekking.app.features.statement.entity;

import com.trekking.app.core.common.entity.TimestampedEntity;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.statement.enums.ItemKind;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "company_statement_items", schema = "trekking")
public class CompanyStatementItem extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "statement_id", nullable = false)
    private Long statementId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, columnDefinition = "text")
    private ItemKind kind;

    @Column(name = "original_item_id")
    private Long originalItemId;

    @Column(name = "retained_revenue_vnd", nullable = false)
    private Long retainedRevenueVnd;

    @Column(name = "platform_fee_vnd", nullable = false)
    private Long platformFeeVnd;

    @Column(name = "held_amount_vnd", nullable = false)
    private Long heldAmountVnd = 0L;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Setter(AccessLevel.NONE)
    @Column(name = "payable_vnd", insertable = false, updatable = false)
    private Long payableVnd;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "statement_id", referencedColumnName = "id", insertable = false, updatable = false),
        @JoinColumn(name = "company_id", referencedColumnName = "company_id", insertable = false, updatable = false)
    })
    @Setter(AccessLevel.NONE)
    private CompanyStatement statement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "booking_id", referencedColumnName = "id", insertable = false, updatable = false),
        @JoinColumn(name = "company_id", referencedColumnName = "company_id", insertable = false, updatable = false)
    })
    @Setter(AccessLevel.NONE)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumns({
        @JoinColumn(name = "original_item_id", referencedColumnName = "id", insertable = false, updatable = false),
        @JoinColumn(name = "booking_id", referencedColumnName = "booking_id", insertable = false, updatable = false),
        @JoinColumn(name = "company_id", referencedColumnName = "company_id", insertable = false, updatable = false)
    })
    @Setter(AccessLevel.NONE)
    private CompanyStatementItem originalItem;
}
