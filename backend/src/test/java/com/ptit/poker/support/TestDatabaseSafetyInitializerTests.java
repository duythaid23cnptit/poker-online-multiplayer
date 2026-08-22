package com.ptit.poker.support;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDatabaseSafetyInitializerTests {

    private final TestDatabaseSafetyInitializer initializer = new TestDatabaseSafetyInitializer();

    @Test
    void acceptsTheDedicatedTestDatabase() {
        GenericApplicationContext context = contextWithUrl(
                "jdbc:mysql://localhost:3306/poker_online_test?connectionTimeZone=UTC");

        assertThatCode(() -> initializer.initialize(context)).doesNotThrowAnyException();
    }

    @Test
    void rejectsTheDevelopmentDatabaseBeforeContextRefresh() {
        GenericApplicationContext context = contextWithUrl(
                "jdbc:mysql://localhost:3306/poker_online");

        assertThatThrownBy(() -> initializer.initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("poker_online_test");
    }

    private static GenericApplicationContext contextWithUrl(String url) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(new MockEnvironment().withProperty("spring.datasource.url", url));
        return context;
    }
}

