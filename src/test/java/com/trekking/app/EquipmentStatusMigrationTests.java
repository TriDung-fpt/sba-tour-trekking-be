package com.trekking.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class EquipmentStatusMigrationTests extends AuthTestInfrastructure {
    @Test
    void migratesExistingDeletionAndInventoryStatesWithoutChangingQuantities() throws Exception {
        String database = "equipment_status_" + UUID.randomUUID().toString().replace("-", "");
        String base = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/";
        try (var admin = DriverManager.getConnection(base + "auth_test", "auth_test", "auth_test");
             var statement = admin.createStatement()) {
            statement.execute("create database " + database);
            try {
                String url = base + database;
                Flyway.configure().dataSource(url, "auth_test", "auth_test").schemas("trekking")
                        .defaultSchema("trekking").target("27").load().migrate();
                try (var connection = DriverManager.getConnection(url, "auth_test", "auth_test");
                     var sql = connection.createStatement()) {
                    sql.execute("insert into trekking.equipment(id,name,deleted) overriding system value values (1,'active',false),(2,'maintenance',false),(3,'deleted',true)");
                    sql.execute("insert into trekking.equipment_inventory(equipment_id,total_quantity,maintenance_quantity,is_active) values (1,10,2,true),(2,4,0,false),(3,5,0,true)");
                }
                Flyway.configure().dataSource(url, "auth_test", "auth_test").schemas("trekking")
                        .defaultSchema("trekking").load().migrate();
                try (var connection = DriverManager.getConnection(url, "auth_test", "auth_test");
                     var sql = connection.createStatement();
                     var rows = sql.executeQuery("select e.status, i.total_quantity, i.maintenance_quantity from trekking.equipment e join trekking.equipment_inventory i on i.equipment_id=e.id order by e.id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("active");
                    assertThat(rows.getInt(3)).isEqualTo(2);
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("maintenance");
                    assertThat(rows.getInt(2)).isEqualTo(4);
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("deleted");
                    assertThat(rows.getInt(2)).isEqualTo(5);
                    assertThat(rows.next()).isFalse();
                }
            } finally {
                statement.execute("drop database " + database + " with (force)");
            }
        }
    }
}
