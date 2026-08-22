package com.ptit.poker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("bootstrap")
@SpringBootTest(properties = "app.security.jwt.secret=cG9rZXItb25saW5lLXRlc3Qtc2lnbmluZy1rZXktZm9yLW9ubHktdGVzdHM=")
class PokerApplicationTests {

    @Test
    void contextLoads() {
    }
}
