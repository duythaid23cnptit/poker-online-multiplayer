package com.ptit.poker.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public final class TestDatabaseSafetyInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String REQUIRED_DATABASE = "poker_online_test";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        String url = applicationContext.getEnvironment().getRequiredProperty("spring.datasource.url");
        String databaseName = databaseNameFrom(url);

        if (!REQUIRED_DATABASE.equals(databaseName)) {
            throw new IllegalStateException(
                    "Refusing database integration tests: TEST_DB_URL must target " + REQUIRED_DATABASE);
        }
    }

    private static String databaseNameFrom(String jdbcUrl) {
        int queryStart = jdbcUrl.indexOf('?');
        String withoutQuery = queryStart >= 0 ? jdbcUrl.substring(0, queryStart) : jdbcUrl;
        int lastSlash = withoutQuery.lastIndexOf('/');
        if (!withoutQuery.startsWith("jdbc:mysql://") || lastSlash < "jdbc:mysql://".length()) {
            return "";
        }
        return withoutQuery.substring(lastSlash + 1);
    }
}

