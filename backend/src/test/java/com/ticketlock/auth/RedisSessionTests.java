package com.ticketlock.auth;

import com.ticketlock.TicketlockApplication;
import com.ticketlock.user.entity.Role;
import com.ticketlock.user.entity.User;
import com.ticketlock.user.repository.UserRepository;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Chạy tường minh khi PostgreSQL và Redis local đã sẵn sàng. */
@EnabledIfEnvironmentVariable(named = "TICKETLOCK_REDIS_TEST", matches = "true")
class RedisSessionTests {
    @Test
    void sharesSessionAcrossInstancesSurvivesRestartAndEnforcesConcurrency() throws Exception {
        String namespace = "ticketlock:test:" + UUID.randomUUID();
        ConfigurableApplicationContext first = null;
        ConfigurableApplicationContext second = null;
        UUID accountId = null;
        try {
            first = start(namespace);
            second = start(namespace);
            var users = second.getBean(UserRepository.class);
            var encoder = second.getBean(PasswordEncoder.class);
            var json = second.getBean(ObjectMapper.class);
            String email = "redis-test-" + UUID.randomUUID() + "@example.test";
            accountId = users.saveAndFlush(new User(email, encoder.encode("TestPassword123!"), Role.CUSTOMER)).getId();
            var oldClient = client();
            var newClient = client();

            login(oldClient, base(first), email, json);
            assertEquals(200, get(oldClient, base(second) + "/me").statusCode());

            first.close();
            first = null;
            first = start(namespace);
            assertEquals(200, get(oldClient, base(first) + "/me").statusCode());

            login(newClient, base(second), email, json);
            assertEquals(401, get(oldClient, base(first) + "/me").statusCode());
            assertEquals(200, get(newClient, base(first) + "/me").statusCode());
            assertEquals(200, get(newClient, base(second) + "/me").statusCode());

            var csrf = json.readTree(get(newClient, base(second) + "/api/auth/csrf").body());
            var logout = HttpRequest.newBuilder(URI.create(base(second) + "/api/auth/logout"))
                    .timeout(Duration.ofSeconds(10))
                    .header(csrf.get("headerName").asString(), csrf.get("token").asString())
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            assertEquals(204, newClient.send(logout, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(401, get(newClient, base(first) + "/me").statusCode());
        } finally {
            try {
                if (accountId != null && second != null) {
                    second.getBean(UserRepository.class).deleteById(accountId);
                }
            } finally {
                if (first != null) first.close();
                if (second != null) second.close();
            }
        }
    }

    private ConfigurableApplicationContext start(String namespace) {
        return new SpringApplicationBuilder(TicketlockApplication.class).profiles("redis").run(
                "--server.port=0", "--spring.session.data.redis.namespace=" + namespace,
                "--spring.main.banner-mode=off", "--logging.level.root=WARN");
    }

    private String base(ConfigurableApplicationContext context) {
        return "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }

    private HttpResponse<String> get(HttpClient client, String address) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(address)).timeout(Duration.ofSeconds(10))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private void login(HttpClient client, String base, String email, ObjectMapper json) throws Exception {
        var csrfResponse = get(client, base + "/api/auth/csrf");
        assertEquals(200, csrfResponse.statusCode());
        var csrf = json.readTree(csrfResponse.body());
        var request = HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .header(csrf.get("headerName").asString(), csrf.get("token").asString())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                        Map.of("email", email, "password", "TestPassword123!")))).build();
        assertEquals(204, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
