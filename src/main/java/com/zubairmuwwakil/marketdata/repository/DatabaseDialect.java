package com.zubairmuwwakil.marketdata.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Which database we are actually talking to.
 *
 * <p>Exists because upsert syntax differs and the demo profile runs H2 with no
 * Postgres at all — a first-class product mode here, not a test convenience.
 */
public final class DatabaseDialect {

    private DatabaseDialect() {}

    public static boolean isH2(DataSource dataSource) {
        if (dataSource == null) {
            return false;
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("h2");
        } catch (SQLException ignored) {
            return false;
        }
    }
}
