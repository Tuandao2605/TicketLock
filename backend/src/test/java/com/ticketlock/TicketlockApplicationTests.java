package com.ticketlock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.session.data.redis.namespace=ticketlock:test:mockmvc")
class TicketlockApplicationTests {

	@Test
	void contextLoads() {
	}

}
