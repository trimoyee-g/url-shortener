package com.urlshortener.url_shortener.integration;

import com.redis.testcontainers.RedisStackContainer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("url_shortener")
                    .withUsername("root")
                    .withPassword("root");

    @Container
    static final RedisStackContainer redis =
            new RedisStackContainer(DockerImageName.parse("redis/redis-stack:latest"));

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

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

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        // Tell the JsonDeserializer exactly which class to deserialize into
        registry.add("spring.kafka.consumer.properties.spring.json.value.default.type",
                () -> "com.urlshortener.url_shortener.dto.UrlClickEvent");

        // Disable Cuckoo Filter population on startup
        registry.add("app.cuckoo-filter.enabled", () -> "false");
    }
}