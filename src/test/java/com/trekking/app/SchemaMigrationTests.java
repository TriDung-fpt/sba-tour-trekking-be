package com.trekking.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.assertj.core.api.Assertions.*;

class SchemaMigrationTests extends AuthTestInfrastructure {
    @Test
    void companyRemovalFreshInstallHasOnlyOperatorDataAndRetainsScalarIntegrity() throws Exception {
        String database = "operator_only_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = connection("auth_test")) {
            assertThat(value(admin, "select current_database()")).isEqualTo("auth_test");
            execute(admin, "create database " + database);
            try (Connection test = connection(database)) {
                initializeThroughSingleOperatorFoundation(test);
                execute(test, Files.readString(Path.of("schema/migrations/006_remove_company_domain.sql")));
                assertOperatorSchema(test);
                execute(test, """
                        insert into trekking.app_accounts(email,password_hash,full_name) values
                            ('admin@example.invalid','hash','Admin'),('guide@example.invalid','hash','Guide'),
                            ('customer@example.invalid','hash','Customer');
                        insert into trekking.account_roles(account_id,role) values (1,'admin'),(2,'guide'),(3,'customer');
                        insert into trekking.guides(account_id) values (2);
                        insert into trekking.policies(code,title,kind,created_by) values ('CANCEL','Cancellation','cancellation',1);
                        insert into trekking.policy_versions(policy_id,version_number,content,effective_from,approved_by,approved_at)
                            values (1,1,'Customer terms',current_timestamp,1,current_timestamp);
                        insert into trekking.tours(title,cancellation_policy_version_id) values ('System tour',1);
                        insert into trekking.tour_schedules(tour_id,starts_at,ends_at,capacity,base_price_vnd,deposit_percent,balance_due_at)
                            values (1,'2027-01-10T00:00:00Z','2027-01-12T00:00:00Z',10,1000000,50,'2027-01-09T00:00:00Z');
                        insert into trekking.schedule_guides(schedule_id,guide_id) values (1,1);
                        insert into trekking.bookings(booking_code,idempotency_key,customer_id,schedule_id,participant_count,
                            includes_equipment,base_price_vnd,equipment_fee_vnd,deposit_percent,cancellation_policy_version_id,
                            policy_snapshot,hold_expires_at,balance_due_at)
                            values ('SYSTEM','SYSTEM-KEY',3,1,2,false,1000000,0,50,1,'{"terms":"customer snapshot"}',
                            '2027-01-01T00:00:00Z','2027-01-09T00:00:00Z');
                        insert into trekking.customer_payments(booking_id,provider,transaction_reference,amount_vnd,received_at,verified_by)
                            values (1,'manual','SYSTEM-PAYMENT',1000000,current_timestamp,1);
                        update trekking.bookings set lock_version=lock_version+1 where id=1;
                        """);
                assertThat(value(test, "select total_price_vnd from trekking.bookings where id=1")).isEqualTo(2000000L);
                assertThat(value(test, "select price_locked_at is not null from trekking.bookings where id=1")).isEqualTo(true);
                rejects(test, "update trekking.bookings set base_price_vnd=1 where id=1", "P0001");
                rejects(test, "update trekking.bookings set policy_snapshot='{}' where id=1", "P0001");
                rejects(test, "update trekking.bookings set price_locked_at=null where id=1", "P0001");
                rejects(test, "update trekking.policy_versions set content='Changed' where id=1", "P0001");
                rejects(test, "insert into trekking.schedule_guides(schedule_id,guide_id) values (999,1)", "23503");
                rejects(test, "insert into trekking.schedule_guides(schedule_id,guide_id) values (1,999)", "23503");
                rejects(test, "update trekking.bookings set schedule_id=999 where id=1", "P0001");
                rejects(test, "delete from trekking.tour_schedules where id=1", "23503");
                rejects(test, "insert into trekking.policies(code,title,kind,created_by) values ('COOP','Forbidden','cooperation',1)", "23514");
                assertThat(value(test, "select count(*) from legacy_archive.snapshot_manifest")).isEqualTo(18L);
                assertThat(value(test, "select count(*) from legacy_archive.snapshot_manifest where row_count<>0")).isEqualTo(0L);
                assertThat(value(test, """
                        select count(*) from pg_constraint constraint_info
                        join pg_class source_table on source_table.oid=constraint_info.conrelid
                        join pg_namespace source_schema on source_schema.oid=source_table.relnamespace
                        where constraint_info.contype='f' and source_schema.nspname='legacy_archive'
                        """)).isEqualTo(0L);
            } finally {
                execute(admin, "drop database " + database);
            }
        }
    }

    @Test
    void companyRemovalUpgradeArchivesOriginalEvidenceAndPreservesCustomerMoneyAndSystemGuides() throws Exception {
        String database = "company_removal_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = connection("auth_test")) {
            assertThat(value(admin, "select current_database()")).isEqualTo("auth_test");
            execute(admin, "create database " + database);
            try (Connection test = connection(database)) {
                initializeThroughSingleOperatorFoundation(test);
                execute(test, """
                        insert into trekking.app_accounts(email,password_hash,full_name) values
                            ('admin@example.invalid','admin-hash','Admin'),('guide@example.invalid','guide-hash','Guide'),
                            ('customer@example.invalid','customer-hash','Customer');
                        insert into trekking.account_roles(account_id,role) values (1,'admin'),(2,'guide'),(3,'customer');
                        insert into trekking.companies(name) values ('Legacy company');
                        insert into trekking.company_members(company_id,account_id,member_role) values (1,1,'owner');
                        insert into trekking.guides(company_id,account_id) values (1,2);
                        insert into trekking.policies(code,title,kind,created_by,company_id) values
                            ('COOP','Agreement','cooperation',1,1),('CANCEL','Cancellation','cancellation',1,null),
                            ('GENERAL','Historic general terms','general',1,null);
                        insert into trekking.policy_versions(policy_id,version_number,content,effective_from) values
                            (1,1,'Legacy cooperation content',current_timestamp),(2,1,'Customer cancellation content',current_timestamp),
                            (3,1,'Historically agreed version without approval metadata',current_timestamp);
                        insert into trekking.company_agreements(company_id,policy_version_id,accepted_by,accepted_at,email_verified_at,
                            platform_fee_percent,payout_wait_hours,payout_terms_snapshot) values
                            (1,1,1,current_timestamp,current_timestamp,8.5,96,'Legacy payout terms'),
                            (1,3,1,current_timestamp,current_timestamp,8.5,96,'Historical evidence');
                        insert into trekking.tours(company_id,title,cancellation_policy_version_id) values (1,'Legacy tour',2);
                        insert into trekking.tour_schedules(tour_id,company_id,starts_at,ends_at,capacity,base_price_vnd,deposit_percent,
                            balance_due_at,status,actual_completed_at,completed_by)
                            values (1,1,'2026-01-10T00:00:00Z','2026-01-12T00:00:00Z',10,1000000,50,
                            '2026-01-09T00:00:00Z','completed','2026-01-12T00:00:00Z',2);
                        insert into trekking.schedule_guides(schedule_id,guide_id,company_id) values (1,1,1);
                        insert into trekking.bookings(booking_code,idempotency_key,customer_id,schedule_id,company_id,participant_count,
                            includes_equipment,base_price_vnd,equipment_fee_vnd,deposit_percent,platform_fee_percent,
                            cancellation_policy_version_id,company_agreement_id,policy_snapshot,equipment_snapshot,hold_expires_at,balance_due_at,status)
                            values ('LEGACY','LEGACY-KEY',3,1,1,1,true,1000000,50000,50,8.5,2,1,
                            '{"terms":"original cancellation"}','{"equipment":"original equipment"}',
                            '2026-01-01T00:00:00Z','2026-01-09T00:00:00Z','completed');
                        insert into trekking.customer_payments(booking_id,provider,transaction_reference,amount_vnd,received_at,verified_by)
                            values (1,'manual','LEGACY-PAYMENT',1050000,'2026-01-01T00:00:00Z',1);
                        insert into trekking.refund_requests(booking_id,requested_by,reason,requested_amount_vnd,idempotency_key)
                            values (1,3,'Partial refund',100000,'REFUND-REQUEST');
                        insert into trekking.customer_refunds(refund_request_id,booking_id,original_payment_id,amount_vnd,channel,
                            transaction_reference,idempotency_key,refunded_at,recorded_by,evidence_url)
                            values (1,1,1,100000,'bank_transfer','LEGACY-REFUND','REFUND-KEY','2026-01-13T00:00:00Z',1,'refund-evidence');
                        insert into trekking.company_statements(company_id,period_start,period_end,cutoff_at,prepared_by)
                            values (1,'2026-01-01','2026-01-07','2026-01-08T00:00:00Z',1);
                        insert into trekking.company_statement_items(statement_id,booking_id,company_id,kind,retained_revenue_vnd,platform_fee_vnd)
                            values (1,1,1,'sale',950000,80750);
                        insert into trekking.company_transfers(statement_id,company_id,amount_vnd,bank_reference,idempotency_key,
                            transferred_at,recorded_by,evidence_url)
                            values (1,1,869250,'LEGACY-PAYOUT','PAYOUT-KEY','2026-01-14T00:00:00Z',1,'payout-evidence');
                        insert into trekking.support_requests(requester_id,requester_company_id,statement_id,subject,description,category)
                            values (1,1,1,'Legacy settlement question','Preserve this conversation','statement');
                        insert into trekking.support_messages(support_request_id,author_id,body) values (1,1,'Original support message');
                        insert into trekking.chats(owner_id,support_request_id) values (3,1);
                        insert into trekking.participants(booking_id,account_id,full_name,checked_in_at,checked_in_by)
                            values (1,3,'Customer','2026-01-10T00:00:00Z',2);
                        insert into trekking.reviews(booking_id,author_id,participant_id,tour_rating,company_rating,company_reply,replied_by,replied_at)
                            values (1,3,1,5,4,'Original operator reply',1,'2026-01-15T00:00:00Z');
                        """);
                String originalBooking = (String) value(test, "select to_jsonb(booking)::text from trekking.bookings booking where id=1");
                String originalPayment = (String) value(test, "select to_jsonb(payment)::text from trekking.customer_payments payment where id=1");
                String originalRefund = (String) value(test, "select to_jsonb(refund)::text from trekking.customer_refunds refund where id=1");
                String originalTransfer = (String) value(test, "select to_jsonb(transfer)::text from trekking.company_transfers transfer where id=1");
                String originalAgreement = (String) value(test, "select to_jsonb(agreement)::text from trekking.company_agreements agreement where id=1");
                execute(test, Files.readString(Path.of("schema/migrations/006_remove_company_domain.sql")));
                assertOperatorSchema(test);
                assertThat(value(test, "select to_jsonb(booking)::text from legacy_archive.bookings booking where id=1")).isEqualTo(originalBooking);
                assertThat(value(test, "select to_jsonb(payment)::text from trekking.customer_payments payment where id=1")).isEqualTo(originalPayment);
                assertThat(value(test, "select to_jsonb(refund)::text from trekking.customer_refunds refund where id=1")).isEqualTo(originalRefund);
                assertThat(value(test, "select to_jsonb(transfer)::text from legacy_archive.company_transfers transfer where id=1")).isEqualTo(originalTransfer);
                assertThat(value(test, "select to_jsonb(agreement)::text from legacy_archive.company_agreements agreement where id=1")).isEqualTo(originalAgreement);
                assertThat(value(test, """
                        select to_jsonb(booking)::text = (select (to_jsonb(archived) - 'company_id' - 'company_agreement_id' - 'platform_fee_percent')::text
                            from legacy_archive.bookings archived where id=1) from trekking.bookings booking where id=1
                        """)).isEqualTo(true);
                assertThat(value(test, "select account_id from trekking.guides where id=1")).isEqualTo(2L);
                assertThat(value(test, "select active from trekking.guides where id=1")).isEqualTo(true);
                assertThat(value(test, "select count(*) from trekking.schedule_guides where schedule_id=1 and guide_id=1")).isEqualTo(1L);
                assertThat(value(test, "select count(*) from trekking.policies where kind='cooperation'")).isEqualTo(0L);
                assertThat(value(test, "select count(*) from legacy_archive.policies where kind='cooperation'")).isEqualTo(1L);
                assertThat(value(test, "select locked_for_history from trekking.policy_versions where id=3")).isEqualTo(true);
                assertThat(value(test, "select approved_at from trekking.policy_versions where id=3")).isNull();
                assertThat(value(test, "select category from trekking.support_requests where id=1")).isEqualTo("other");
                assertThat(value(test, "select booking_id from trekking.support_requests where id=1")).isNull();
                assertThat(value(test, "select body from trekking.support_messages where id=1")).isEqualTo("Original support message");
                assertThat(value(test, "select support_request_id from trekking.chats where id=1")).isEqualTo(1L);
                assertThat(value(test, "select service_rating from trekking.reviews where id=1")).isEqualTo((short) 4);
                assertThat(value(test, "select operator_reply from trekking.reviews where id=1")).isEqualTo("Original operator reply");
                rejects(test, "update trekking.bookings set base_price_vnd=1 where id=1", "P0001");
                rejects(test, "update trekking.policy_versions set content='Changed' where id=2", "P0001");
                rejects(test, "update trekking.policy_versions set locked_for_history=false where id=3", "P0001");
                rejects(test, "delete from trekking.customer_payments where id=1", "P0001");
                rejects(test, "delete from trekking.customer_refunds where id=1", "P0001");
                rejects(test, "update legacy_archive.company_transfers set amount_vnd=1 where id=1", "23514");
                rejects(test, "delete from legacy_archive.bookings where id=1", "23514");
                rejects(test, "update trekking.reviews set service_rating=0 where id=1", "23514");
                rejects(test, "insert into trekking.support_requests(requester_id,subject,description,category) values (1,'Invalid','Invalid','booking')", "23514");
            } finally {
                execute(admin, "drop database " + database);
            }
        }
    }

    @Test
    void companyRemovalRefusesCooperationUsedAsCustomerPolicyAndRollsBack() throws Exception {
        String database = "invalid_policy_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = connection("auth_test")) {
            assertThat(value(admin, "select current_database()")).isEqualTo("auth_test");
            execute(admin, "create database " + database);
            try (Connection test = connection(database)) {
                initializeThroughSingleOperatorFoundation(test);
                execute(test, """
                        insert into trekking.app_accounts(email,password_hash,full_name) values ('admin@example.invalid','hash','Admin');
                        insert into trekking.policies(code,title,kind,created_by) values ('COOP','Cooperation','cooperation',1);
                        insert into trekking.policy_versions(policy_id,version_number,content,effective_from) values (1,1,'Legacy terms',current_timestamp);
                        insert into trekking.tours(title,cancellation_policy_version_id) values ('Invalid policy assignment',1);
                        """);
                rejects(test, Files.readString(Path.of("schema/migrations/006_remove_company_domain.sql")), "23514");
                execute(test, "rollback");
                assertThat(value(test, "select count(*) from trekking.policies where kind='cooperation'")).isEqualTo(1L);
                assertThat(value(test, "select to_regnamespace('legacy_archive')")).isNull();
                assertThat(value(test, "select count(*) from information_schema.columns where table_schema='trekking' and table_name='bookings' and column_name='company_id'"))
                        .isEqualTo(1L);
            } finally {
                execute(admin, "drop database " + database);
            }
        }
    }

    @Test
    void singleOperatorFreshInstallSupportsInternalDataAndEnforcesForeignKeysWithoutCompany() throws Exception {
        String database = "operator_fresh_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = connection("auth_test")) {
            assertThat(value(admin, "select current_database()")).isEqualTo("auth_test");
            execute(admin, "create database " + database);
            try (Connection test = connection(database)) {
                initializeThroughFinancialPermissions(test);
                execute(test, Files.readString(Path.of("schema/migrations/005_single_operator_foundation.sql")));
                execute(test, """
                        insert into trekking.app_accounts(email,password_hash,full_name) values
                            ('admin@example.invalid','hash','Admin'),
                            ('guide@example.invalid','hash','Guide'),
                            ('customer@example.invalid','hash','Customer');
                        insert into trekking.account_roles(account_id,role) values (1,'admin'),(2,'guide'),(3,'customer');
                        insert into trekking.guides(account_id) values (2);
                        insert into trekking.policies(code,title,kind,created_by) values ('CANCEL','Cancellation','cancellation',1);
                        insert into trekking.policy_versions(policy_id,version_number,content,effective_from)
                            values (1,1,'Cancellation snapshot',current_timestamp);
                        insert into trekking.tours(title,cancellation_policy_version_id) values ('Internal tour',1);
                        insert into trekking.tour_schedules(tour_id,starts_at,ends_at,capacity,base_price_vnd,deposit_percent,balance_due_at)
                            values (1,'2027-01-10T00:00:00Z','2027-01-12T00:00:00Z',10,1000000,50,'2027-01-09T00:00:00Z');
                        insert into trekking.schedule_guides(schedule_id,guide_id,is_lead) values (1,1,true);
                        insert into trekking.bookings(booking_code,idempotency_key,customer_id,schedule_id,participant_count,
                            includes_equipment,base_price_vnd,equipment_fee_vnd,deposit_percent,cancellation_policy_version_id,
                            policy_snapshot,hold_expires_at,balance_due_at)
                            values ('INTERNAL','INTERNAL-KEY',3,1,2,false,1000000,0,50,1,'{"terms":"snapshot"}',
                            '2027-01-01T00:00:00Z','2027-01-09T00:00:00Z');
                        """);
                assertThat(value(test, "select count(*) from trekking.companies")).isEqualTo(0L);
                assertThat(value(test, "select company_id from trekking.guides where id=1")).isNull();
                assertThat(value(test, "select company_id from trekking.bookings where id=1")).isNull();
                assertThat(value(test, "select platform_fee_percent::text from trekking.bookings where id=1")).isEqualTo("0.00");
                assertThat(value(test, "select total_price_vnd from trekking.bookings where id=1")).isEqualTo(2000000L);
                for (String role : List.of("company_manager", "support_staff", "content_staff", "accountant")) {
                    rejects(test, "insert into trekking.account_roles(account_id,role) values (1,'" + role + "')", "23514");
                }
                rejects(test, """
                        insert into trekking.tour_schedules(tour_id,starts_at,ends_at,capacity,base_price_vnd,deposit_percent,balance_due_at)
                            values (999,'2027-01-10T00:00:00Z','2027-01-12T00:00:00Z',10,1000000,50,'2027-01-09T00:00:00Z')
                        """, "23503");
                rejects(test, "insert into trekking.schedule_guides(schedule_id,guide_id) values (999,1)", "23503");
                rejects(test, "insert into trekking.schedule_guides(schedule_id,guide_id) values (1,999)", "23503");
                rejects(test, """
                        insert into trekking.bookings(booking_code,idempotency_key,customer_id,schedule_id,participant_count,
                            includes_equipment,base_price_vnd,equipment_fee_vnd,deposit_percent,cancellation_policy_version_id,
                            policy_snapshot,hold_expires_at,balance_due_at)
                            values ('MISSING-SCHEDULE','MISSING-SCHEDULE-KEY',3,999,1,false,1000000,0,50,1,'{}',
                            '2027-01-01T00:00:00Z','2027-01-09T00:00:00Z')
                        """, "23503");
                rejects(test, "delete from trekking.tour_schedules where id=1", "23503");
                rejects(test, "delete from trekking.guides where id=1", "23503");
                rejects(test, "delete from trekking.tours where id=1", "23503");
                rejects(test, "update trekking.bookings set company_agreement_id=999 where id=1", "23514");
                rejects(test, "update trekking.policy_versions set content='Rewritten' where id=1", "P0001");
                rejects(test, "delete from trekking.policy_versions where id=1", "P0001");
                execute(test, """
                        insert into trekking.policy_versions(policy_id,version_number,content,effective_from,approved_by,approved_at)
                            values (1,2,'Published unused version',current_timestamp,1,current_timestamp);
                        insert into trekking.customer_payments(booking_id,provider,transaction_reference,amount_vnd,received_at,verified_by)
                            values (1,'manual','INTERNAL-PAYMENT',1000000,current_timestamp,1);
                        """);
                rejects(test, "update trekking.policy_versions set content='Rewritten' where id=2", "P0001");
                rejects(test, "update trekking.bookings set base_price_vnd=1 where id=1", "P0001");
                rejects(test, "delete from trekking.customer_payments where id=1", "P0001");
                rejects(test, "insert into trekking.account_financial_permissions(account_id,permission,granted_by) values (1,'FINANCE_READ',1)", "23514");
                assertThat(value(test, "select authorization_version from trekking.app_accounts where id=2")).isEqualTo(1L);
                execute(test, "delete from trekking.account_roles where account_id=2 and role='guide'");
                assertThat(value(test, "select authorization_version from trekking.app_accounts where id=2")).isEqualTo(2L);
            } finally {
                execute(admin, "drop database " + database);
            }
        }
    }

    @Test
    void singleOperatorUpgradeArchivesRolesWithoutPromotionAndPreservesPaidLegacyHistory() throws Exception {
        String database = "operator_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = connection("auth_test")) {
            assertThat(value(admin, "select current_database()")).isEqualTo("auth_test");
            execute(admin, "create database " + database);
            try (Connection test = connection(database)) {
                initializeThroughFinancialPermissions(test);
                execute(test, """
                        insert into trekking.app_accounts(email,password_hash,full_name) values
                            ('admin@example.invalid','admin-hash','Admin'),
                            ('accountant@example.invalid','accountant-hash','Accountant'),
                            ('manager-guide@example.invalid','guide-hash','Manager Guide'),
                            ('support-customer@example.invalid','support-hash','Support Customer'),
                            ('content@example.invalid','content-hash','Content'),
                            ('customer@example.invalid','customer-hash','Customer');
                        insert into trekking.account_roles(account_id,role) values
                            (1,'admin'),(2,'accountant'),(3,'company_manager'),(3,'guide'),
                            (4,'support_staff'),(4,'customer'),(5,'content_staff'),(6,'customer');
                        insert into trekking.account_financial_permissions(account_id,permission,granted_by,granted_at)
                            values (2,'FINANCE_READ',1,'2026-01-01T00:00:00Z'),(2,'PAYMENT_RECORD',1,'2026-01-02T00:00:00Z');
                        insert into trekking.companies(name) values ('Legacy company');
                        insert into trekking.guides(company_id,account_id) values (1,3);
                        insert into trekking.policies(code,title,kind,created_by) values
                            ('COOP','Legacy agreement','cooperation',1),('CANCEL','Cancellation','cancellation',1);
                        insert into trekking.policy_versions(policy_id,version_number,content,effective_from,approved_by,approved_at)
                            values (1,1,'Cooperation snapshot',current_timestamp,1,current_timestamp),
                            (2,1,'Cancellation snapshot',current_timestamp,1,current_timestamp);
                        insert into trekking.company_agreements(company_id,policy_version_id,accepted_by,accepted_at,email_verified_at,
                            platform_fee_percent,payout_wait_hours,payout_terms_snapshot)
                            values (1,1,3,current_timestamp,current_timestamp,8.5,96,'Legacy settlement terms');
                        insert into trekking.tours(company_id,title,cancellation_policy_version_id) values (1,'Legacy tour',2);
                        insert into trekking.tour_schedules(tour_id,company_id,starts_at,ends_at,capacity,base_price_vnd,deposit_percent,balance_due_at)
                            values (1,1,'2027-01-10T00:00:00Z','2027-01-12T00:00:00Z',10,1000000,50,'2027-01-09T00:00:00Z');
                        insert into trekking.schedule_guides(schedule_id,guide_id,company_id) values (1,1,1);
                        insert into trekking.bookings(booking_code,idempotency_key,customer_id,schedule_id,company_id,participant_count,
                            includes_equipment,base_price_vnd,equipment_fee_vnd,deposit_percent,platform_fee_percent,
                            cancellation_policy_version_id,company_agreement_id,policy_snapshot,equipment_snapshot,hold_expires_at,balance_due_at)
                            values ('LEGACY','LEGACY-KEY',6,1,1,2,true,1000000,50000,50,8.5,2,1,
                            '{"terms":"original cancellation"}','{"equipment":"original equipment"}',
                            '2027-01-01T00:00:00Z','2027-01-09T00:00:00Z');
                        insert into trekking.customer_payments(booking_id,provider,transaction_reference,amount_vnd,received_at,verified_by)
                            values (1,'manual','LEGACY-PAYMENT',2100000,'2026-10-01T00:00:00Z',2);
                        insert into trekking.refund_requests(booking_id,requested_by,reason,requested_amount_vnd,idempotency_key)
                            values (1,6,'Partial refund',100000,'REFUND-REQUEST');
                        insert into trekking.customer_refunds(refund_request_id,booking_id,original_payment_id,amount_vnd,channel,
                            transaction_reference,idempotency_key,refunded_at,recorded_by,evidence_url)
                            values (1,1,1,100000,'bank_transfer','LEGACY-REFUND','REFUND-KEY','2026-10-02T00:00:00Z',2,'legacy-refund-evidence');
                        insert into trekking.company_statements(company_id,period_start,period_end,cutoff_at,prepared_by)
                            values (1,'2026-10-01','2026-10-07','2026-10-08T00:00:00Z',2);
                        insert into trekking.company_statement_items(statement_id,booking_id,company_id,kind,retained_revenue_vnd,platform_fee_vnd)
                            values (1,1,1,'sale',2000000,170000);
                        insert into trekking.company_transfers(statement_id,company_id,amount_vnd,bank_reference,idempotency_key,
                            transferred_at,recorded_by,evidence_url)
                            values (1,1,1830000,'LEGACY-PAYOUT','PAYOUT-KEY','2026-10-09T00:00:00Z',2,'legacy-payout-evidence');
                        """);
                String historyQuery = """
                        select jsonb_build_object(
                            'booking',(select to_jsonb(b) from trekking.bookings b where id=1),
                            'agreement',(select to_jsonb(a) from trekking.company_agreements a where id=1),
                            'payment',(select to_jsonb(p) from trekking.customer_payments p where id=1),
                            'refund',(select to_jsonb(r) from trekking.customer_refunds r where id=1),
                            'statement',(select to_jsonb(s) from trekking.company_statements s where id=1),
                            'statementItem',(select to_jsonb(i) from trekking.company_statement_items i where id=1),
                            'transfer',(select to_jsonb(t) from trekking.company_transfers t where id=1))::text
                        """;
                Object history = value(test, historyQuery);
                Object grants = value(test, "select jsonb_agg(to_jsonb(p) order by permission)::text from trekking.account_financial_permissions p");
                Object retiredRoles = value(test, "select jsonb_agg(to_jsonb(r) order by account_id,role)::text from trekking.account_roles r "
                        + "where role not in ('admin','guide','customer')");
                execute(test, Files.readString(Path.of("schema/migrations/005_single_operator_foundation.sql")));
                assertThat(value(test, historyQuery)).isEqualTo(history);
                assertThat(value(test, "select jsonb_agg(to_jsonb(p) order by permission)::text from trekking.account_financial_permissions p")).isEqualTo(grants);
                assertThat(value(test, "select jsonb_agg(to_jsonb(r) - 'archived_at' order by account_id,role)::text from trekking.legacy_account_roles r"))
                        .isEqualTo(retiredRoles);
                assertThat(value(test, "select jsonb_agg(to_jsonb(p) - 'archived_at' order by permission)::text from trekking.legacy_account_financial_permissions p"))
                        .isEqualTo(grants);
                assertThat(value(test, "select count(*) from trekking.legacy_account_roles")).isEqualTo(4L);
                assertThat(value(test, "select count(*) from trekking.legacy_account_financial_permissions")).isEqualTo(2L);
                assertThat(value(test, "select granted_at::date::text from trekking.legacy_account_financial_permissions where permission='PAYMENT_RECORD'")).isEqualTo("2026-01-02");
                assertThat(value(test, "select string_agg(account_id::text || ':' || role, ',' order by account_id,role) from trekking.account_roles"))
                        .isEqualTo("1:admin,2:customer,3:guide,4:customer,5:customer,6:customer");
                assertThat(value(test, "select count(*) from trekking.app_accounts where id in (2,3,4,5) and session_version=1")).isEqualTo(4L);
                assertThat(value(test, "select count(*) from trekking.app_accounts where id in (1,6) and session_version=0")).isEqualTo(2L);
                assertThat(value(test, "select authorization_version from trekking.app_accounts where id=2")).isEqualTo(3L);
                assertThat(value(test, "select password_hash from trekking.app_accounts where id=2")).isEqualTo("accountant-hash");
                assertThat(value(test, "select company_id from trekking.guides where id=1")).isEqualTo(1L);
                rejects(test, "update trekking.bookings set company_id=null where id=1", "P0001");
                rejects(test, "update trekking.bookings set policy_snapshot='{}' where id=1", "P0001");
                rejects(test, "delete from trekking.customer_payments where id=1", "P0001");
                rejects(test, "delete from trekking.customer_refunds where id=1", "P0001");
                rejects(test, "delete from trekking.company_transfers where id=1", "P0001");
                rejects(test, "update trekking.company_agreements set payout_terms_snapshot='Changed' where id=1", "P0001");
                rejects(test, "update trekking.account_financial_permissions set granted_by=2 where account_id=2", "23514");
                rejects(test, "delete from trekking.account_financial_permissions where account_id=2", "23514");
                rejects(test, "delete from trekking.legacy_account_roles where account_id=2", "23514");
                rejects(test, "delete from trekking.legacy_account_financial_permissions where account_id=2", "23514");
                execute(test, "insert into trekking.account_roles(account_id,role) values (2,'guide')");
                assertThat(value(test, "select authorization_version from trekking.app_accounts where id=2")).isEqualTo(4L);
                assertThat(value(test, "select count(*) from trekking.account_financial_permissions where account_id=2")).isEqualTo(2L);
            } finally {
                execute(admin, "drop database " + database);
            }
        }
    }

    @Test
    void financialUpgradePreservesAccountsStartsWithNoGrantsAndInvalidatesRoleChallenges() throws Exception {
        String database = "financial_migration_" + UUID.randomUUID().toString().replace("-", "");
        assertThat(database).matches("[a-z0-9_]+");
        try (Connection admin = connection("auth_test")) {
            execute(admin, "create database " + database);
            try (Connection test = connection(database)) {
                execute(test, Files.readString(Path.of("schema/schema.sql")));
                execute(test, Files.readString(Path.of("schema/migrations/001_add_google_subject.sql")));
                execute(test, Files.readString(Path.of("schema/migrations/002_add_account_session_version.sql")));
                execute(test, Files.readString(Path.of("schema/migrations/003_add_cooperation_policy_terms.sql")));
                execute(test, "insert into trekking.app_accounts(email,password_hash,full_name) values ('legacy@example.invalid','legacy-hash','Legacy')");
                execute(test, "insert into trekking.account_roles(account_id,role) values (1,'accountant')");
                execute(test, Files.readString(Path.of("schema/migrations/004_add_financial_permissions.sql")));
                assertThat(value(test, "select password_hash from trekking.app_accounts where id=1")).isEqualTo("legacy-hash");
                assertThat(value(test, "select authorization_version from trekking.app_accounts where id=1")).isEqualTo(0L);
                assertThat(value(test, "select session_version from trekking.app_accounts where id=1")).isEqualTo(0L);
                assertThat(value(test, "select count(*) from trekking.account_financial_permissions")).isEqualTo(0L);
                execute(test, "insert into trekking.account_financial_permissions(account_id,permission,granted_by) values(1,'FINANCE_READ',1)");
                execute(test, "delete from trekking.account_roles where account_id=1 and role='accountant'");
                assertThat(value(test, "select authorization_version from trekking.app_accounts where id=1")).isEqualTo(1L);
                assertThat(value(test, "select count(*) from trekking.account_financial_permissions")).isEqualTo(0L);
                assertThatThrownBy(() -> execute(test, "insert into trekking.account_financial_permissions(account_id,permission,granted_by) values(1,'FINANCE_READ',1)"))
                        .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23514"));
                execute(test, "insert into trekking.account_roles(account_id,role) values(1,'accountant')");
                assertThat(value(test, "select authorization_version from trekking.app_accounts where id=1")).isEqualTo(2L);
                assertThat(value(test, "select count(*) from trekking.account_financial_permissions")).isEqualTo(0L);
            } finally {
                execute(admin, "drop database " + database);
            }
        }
    }

    @Test
    void cooperationUpgradePreservesLegacyReceiptsAndRequiresExplicitCompleteTerms() throws Exception {
        String database = "terms_migration_" + UUID.randomUUID().toString().replace("-", "");
        assertThat(database).matches("[a-z0-9_]+");
        try (Connection admin = connection("auth_test")) {
            assertThat(value(admin, "select current_database()")).isEqualTo("auth_test");
            execute(admin, "create database " + database);
            try (Connection test = connection(database)) {
                execute(test, Files.readString(Path.of("schema/schema.sql")));
                ScriptUtils.executeSqlScript(test, new FileSystemResource("schema/migrations/001_add_google_subject.sql"));
                ScriptUtils.executeSqlScript(test, new FileSystemResource("schema/migrations/002_add_account_session_version.sql"));
                execute(test, "insert into trekking.app_accounts(email,password_hash,full_name) "
                        + "values ('legacy@example.invalid','preserved-hash','Legacy owner')");
                execute(test, "insert into trekking.companies(name) values ('Legacy company')");
                execute(test, "insert into trekking.policies(code,title,kind,created_by) values ('legacy','Legacy','cooperation',1)");
                execute(test, "insert into trekking.policy_versions(policy_id,version_number,content,effective_from,approved_by,approved_at) "
                        + "values (1,1,'Legacy terms',current_timestamp,1,current_timestamp)");
                execute(test, "insert into trekking.company_agreements(company_id,policy_version_id,accepted_by,accepted_at,email_verified_at,"
                        + "platform_fee_percent,payout_wait_hours,payout_terms_snapshot) values (1,1,1,current_timestamp,current_timestamp,8,96,'Legacy settlement')");
                // Execute the complete PostgreSQL script: Spring's semicolon parser does not parse dollar-quoted PL/pgSQL.
                execute(test, Files.readString(Path.of("schema/migrations/003_add_cooperation_policy_terms.sql")));
                assertThat(value(test, "select platform_fee_percent from trekking.policy_versions where id=1")).isNull();
                assertThat(value(test, "select payout_terms_snapshot from trekking.company_agreements where id=1")).isEqualTo("Legacy settlement");
                assertThat(value(test, "select payout_wait_hours from trekking.company_agreements where id=1")).isEqualTo(96);
                assertThatThrownBy(() -> execute(test, "update trekking.policy_versions set content='Changed' where id=1"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> execute(test, "delete from trekking.company_agreements where id=1"))
                        .isInstanceOf(SQLException.class);
                String insert = "insert into trekking.policy_versions(policy_id,version_number,content,effective_from,platform_fee_percent,payout_wait_hours,payout_terms_snapshot) ";
                assertThatThrownBy(() -> execute(test, insert + "values (1,2,'New',current_timestamp,10,null,null)"))
                        .isInstanceOfSatisfying(SQLException.class, ex -> assertThat(ex.getSQLState()).isEqualTo("23514"));
                assertThatThrownBy(() -> execute(test, insert + "values (1,2,'New',current_timestamp,101,72,'Terms')"))
                        .isInstanceOfSatisfying(SQLException.class, ex -> assertThat(ex.getSQLState()).isEqualTo("23514"));
                execute(test, insert + "values (1,2,'New',current_timestamp,12.5,48,'Explicit terms')");
                assertThat(value(test, "select payout_wait_hours from trekking.policy_versions where version_number=2")).isEqualTo(48);
                assertThat(value(test, "select password_hash from trekking.app_accounts where id=1")).isEqualTo("preserved-hash");
            } finally {
                execute(admin, "drop database " + database);
            }
        }
    }

    @Test
    void sessionVersionUpgradePreservesLegacyAccountsAndRejectsNegativeVersions() throws Exception {
        String database = "session_migration_" + UUID.randomUUID().toString().replace("-", "");
        assertThat(database).matches("[a-z0-9_]+");
        try (Connection admin = connection("auth_test")) {
            assertThat(value(admin, "select current_database()")).isEqualTo("auth_test");
            execute(admin, "create database " + database);
            try (Connection test = connection(database)) {
                execute(test, Files.readString(Path.of("schema/schema.sql")));
                ScriptUtils.executeSqlScript(test, new FileSystemResource("schema/migrations/001_add_google_subject.sql"));
                execute(test, "insert into trekking.app_accounts(email,password_hash,full_name) "
                        + "values ('legacy@example.invalid','preserved-hash','Legacy')");
                ScriptUtils.executeSqlScript(test, new FileSystemResource("schema/migrations/002_add_account_session_version.sql"));
                assertThat(value(test, "select session_version from trekking.app_accounts where id=1")).isEqualTo(0L);
                assertThat(value(test, "select password_hash from trekking.app_accounts where id=1")).isEqualTo("preserved-hash");
                execute(test, "insert into trekking.app_accounts(email,password_hash,full_name) "
                        + "values ('new@example.invalid','new-hash','New')");
                assertThat(value(test, "select session_version from trekking.app_accounts where id=2")).isEqualTo(0L);
                assertThatThrownBy(() -> execute(test, "update trekking.app_accounts set session_version=-1 where id=1"))
                        .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23514"));
            } finally {
                execute(admin, "drop database " + database);
            }
        }
    }

    @Test
    void googleSubjectMigrationPreservesPasswordAccountsAndEnforcesUniqueNonblankSubject() throws Exception {
        String database = "google_migration_" + UUID.randomUUID().toString().replace("-", "");
        assertThat(database).matches("[a-z0-9_]+");
        try (Connection admin = connection("auth_test")) {
            assertThat(value(admin, "select current_database()")).isEqualTo("auth_test");
            execute(admin, "create database " + database);
            try (Connection test = connection(database)) {
                assertThat(value(test, "select current_database()")).isEqualTo(database);
                execute(test, Files.readString(Path.of("schema/schema.sql")));
                execute(test, "insert into trekking.app_accounts(email, password_hash, full_name, email_verified_at) "
                        + "values (' LEGACY@EXAMPLE.INVALID ', 'existing-password-hash', 'Existing Customer', CURRENT_TIMESTAMP)");
                execute(test, "insert into trekking.account_roles(account_id, role) values (1, 'customer')");
                ScriptUtils.executeSqlScript(test, new FileSystemResource("schema/migrations/001_add_google_subject.sql"));
                assertThat(value(test, "select email from trekking.app_accounts where id=1")).isEqualTo(" LEGACY@EXAMPLE.INVALID ");
                assertThat(value(test, "select password_hash from trekking.app_accounts where id=1")).isEqualTo("existing-password-hash");
                assertThat(value(test, "select google_subject from trekking.app_accounts where id=1")).isNull();
                assertThat(value(test, "select role from trekking.account_roles where account_id=1")).isEqualTo("customer");
                execute(test, "insert into trekking.app_accounts(email, password_hash, full_name) "
                        + "values ('second@example.invalid', 'second-password-hash', 'Second Customer')");
                assertThat(value(test, "select count(*) from trekking.app_accounts where google_subject is null")).isEqualTo(2L);
                execute(test, "update trekking.app_accounts set google_subject='google-subject-123' where id=1");
                assertThatThrownBy(() -> execute(test, "update trekking.app_accounts set google_subject='google-subject-123' where id=2"))
                        .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23505"));
                assertThatThrownBy(() -> execute(test, "update trekking.app_accounts set google_subject='   ' where id=2"))
                        .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23514"));
                assertThat(value(test, "select google_subject from trekking.app_accounts where id=2")).isNull();
                assertThat(value(test, "select count(*) from trekking.app_accounts")).isEqualTo(2L);
            } finally {
                // Only this randomly named database on the disposable Testcontainers server is removed.
                assertThat(value(admin, "select current_database()")).isEqualTo("auth_test");
                execute(admin, "drop database " + database);
            }
        }
    }

    private Connection connection(String database) throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/" + database, "auth_test", "auth_test");
    }

    private void initializeThroughFinancialPermissions(Connection connection) throws Exception {
        execute(connection, Files.readString(Path.of("schema/schema.sql")));
        for (String migration : List.of("001_add_google_subject.sql", "002_add_account_session_version.sql",
                "003_add_cooperation_policy_terms.sql", "004_add_financial_permissions.sql")) {
            execute(connection, Files.readString(Path.of("schema/migrations", migration)));
        }
    }

    private void initializeThroughSingleOperatorFoundation(Connection connection) throws Exception {
        initializeThroughFinancialPermissions(connection);
        execute(connection, Files.readString(Path.of("schema/migrations/005_single_operator_foundation.sql")));
    }

    private void assertOperatorSchema(Connection connection) throws SQLException {
        assertThat(value(connection, "select count(*) from information_schema.columns where table_schema='trekking' "
                + "and (column_name like '%company%' or column_name like '%platform_fee%' or column_name like '%payout%')")).isEqualTo(0L);
        assertThat(value(connection, "select count(*) from information_schema.tables where table_schema='trekking' "
                + "and (table_name like '%compan%' or table_name like '%financial_permissions%' or table_name like 'legacy_%')")).isEqualTo(0L);
    }

    private void rejects(Connection connection, String sql, String sqlState) {
        assertThatThrownBy(() -> execute(connection, sql))
                .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo(sqlState));
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private Object value(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getObject(1);
        }
    }
}
