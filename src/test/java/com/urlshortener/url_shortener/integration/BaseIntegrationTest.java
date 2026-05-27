package com.urlshortener.url_shortener.integration;

import com.redis.testcontainers.RedisStackContainer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    // ── Singleton containers ──────────────────────────────────────────────────
    // Started once for the entire test run (JVM lifecycle), not per test class.
    //
    // The previous pattern used @Testcontainers + @Container on static fields.
    // That binds container lifecycle to JUnit 5 class lifecycle: when a class
    // finishes, JUnit stops the containers. The next class then inherits a
    // Spring context (from the cache) that still points to those now-dead
    // containers → every DB call throws CannotCreateTransaction.
    //
    // The singleton pattern avoids this: containers start in the static block,
    // live for the whole JVM, and are cleaned up by Testcontainers' own
    // shutdown hook (Ryuk). Spring context caching works correctly because the
    // ports never change between classes.
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("url_shortener")
                    .withUsername("root")
                    .withPassword("root");

    static final RedisStackContainer redis =
            new RedisStackContainer(DockerImageName.parse("redis/redis-stack:latest"));

    static final RabbitMQContainer rabbitmq =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"));

    static {
        mysql.start();
        redis.start();
        rabbitmq.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // MySQL
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        // RabbitMQ
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbitmq.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);

        // Disable Cuckoo Filter population on startup
        registry.add("app.cuckoo-filter.enabled", () -> "false");
    }
}
