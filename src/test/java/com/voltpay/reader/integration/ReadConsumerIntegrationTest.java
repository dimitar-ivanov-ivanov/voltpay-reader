package com.voltpay.reader.integration;

import com.voltpay.reader.entities.Idempotency;
import com.voltpay.reader.entities.Transaction;
import com.voltpay.reader.pojo.ReadEvent;
import com.voltpay.reader.repositories.IdempotencyRepository;
import com.voltpay.reader.repositories.TransactionRepository;
import com.voltpay.reader.utils.Currency;
import com.voltpay.reader.utils.TrnStatus;
import com.voltpay.reader.utils.TrnType;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.Assert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest // annotation is needed for autowiring
@ActiveProfiles("test")
public class ReadConsumerIntegrationTest {

    private static final String MESSAGE_ID = "msg";
    private static final String RECORD_ID = "id";
    private static final BigDecimal AMOUNT = BigDecimal.TEN;
    private static final LocalDateTime CREATED_AT = LocalDateTime.now();
    private static final LocalDateTime UPDATED_AT = CREATED_AT.plusHours(1);
    private static final String COMMENT = "comment";
    private static final Integer VERSION = 100;
    private static final String CURRENCY = Currency.EUR.toString();
    private static final Long CUST_ID = 1L;
    private static final Integer STATUS = TrnStatus.SUCCESS.getValue();
    private static final String TYPE = TrnType.BWI.toString();

    private static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    private static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("read_db")
        .withUsername("user")
        .withPassword("password")
        .withInitScript("init.sql");

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private KafkaTemplate<String, ReadEvent> kafkaTemplate;

    @BeforeAll
    static void beforeAll() {
        kafka.start();
        postgres.start();

        createTopics();
    }

    private static void createTopics() {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafka.getBootstrapServers());

        try (AdminClient adminClient = AdminClient.create(props)) {
            // Topic name - partitions - retention
            NewTopic readTopic = new NewTopic("read-topic", 1, (short) 1);
            NewTopic dltTopic = new NewTopic("read-dlt", 1, (short) 1);
            adminClient.createTopics(List.of(readTopic, dltTopic)).all().get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create topic", e);
        }
    }

    @AfterAll
    static void afterAll() {
        postgres.stop();
        kafka.stop();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // Postgres config
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Kafka config
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @BeforeEach
    void cleanUpDatabase() {
        transactionRepository.deleteAll();
        idempotencyRepository.deleteAll();
    }

    @Test
    public void given_duplicateIdempotency_when_processMessage_then_onlyPersistOnce() {
        // GIVEN
        ReadEvent event = buildReadEvent();
        // WHEN
        kafkaTemplate.send("read-topic", CUST_ID.toString(), event);
        kafkaTemplate.send("read-topic", CUST_ID.toString(), event);
        // THEN
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(2, TimeUnit.SECONDS).untilAsserted(() -> {
                assertEquals(1, idempotencyRepository.count());
                Optional<Idempotency> idempotency = idempotencyRepository.findById(event.getMessageId());
                assertNotNull(idempotency.get());
                Assert.assertEquals(event.getMessageId(), idempotency.get().getId());
            });
    }

    @Test
    public void given_validEvent_when_processMessage_then_processSuccessfully() {
        // GIVEN
        ReadEvent event = buildReadEvent();
        // WHEN
        kafkaTemplate.send("read-topic", CUST_ID.toString(), event);

        // THEN wait 10 second total to assert, every 2 second we try to do the assertions again
        // CONSIDER increasing timeout when debugging
        await()
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(2, TimeUnit.SECONDS).untilAsserted(() -> {

                assertEquals(1, idempotencyRepository.count());
                Optional<Idempotency> idempotency = idempotencyRepository.findById(event.getMessageId());
                assertNotNull(idempotency.get());
                Assert.assertEquals(event.getMessageId(), idempotency.get().getId());

                List<Transaction> transactions = transactionRepository.findAll();

                Assert.assertEquals(1, transactions.size());
                Transaction transaction = transactions.stream().findFirst().get();

                assertEquals(event.getId(), transaction.getId());

                compareDateTimes(event.getCreatedAt(), transaction.getCreatedAt());
                compareDateTimes(event.getUpdatedAt(), transaction.getUpdatedAt());
                assertEquals(event.getAmount().setScale(6), transaction.getAmount());
                assertEquals(event.getStatus(), transaction.getStatus());
                assertEquals(event.getCurrency(), transaction.getCurrency());
                assertEquals(event.getCustId(), transaction.getCustId());
                assertEquals(event.getType(), transaction.getType());
                assertEquals(event.getComment(), transaction.getComment());
                assertEquals(event.getVersion(), transaction.getVersion());
            });
    }

    /**
     * Compare actual and expected date time.
     * Truncate down to millis as we don't need to be more specific.
     * If we truncate to microseconds the test becomes unstable.
     * @param expectedDate date from event
     * @param actualDate date from database record
     */
    private static void compareDateTimes(LocalDateTime expectedDate, LocalDateTime actualDate) {
        LocalDateTime expected = expectedDate.truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime actual = actualDate.truncatedTo(ChronoUnit.MILLIS);

        assertEquals(expected, actual);
    }

    private ReadEvent buildReadEvent() {
        return ReadEvent.builder()
            .messageId(MESSAGE_ID)
            .id(RECORD_ID)
            .amount(AMOUNT)
            .createdAt(CREATED_AT)
            .currency(CURRENCY)
            .custId(CUST_ID)
            .status(STATUS)
            .type(TYPE)
            .updatedAt(UPDATED_AT)
            .comment(COMMENT)
            .version(VERSION)
            .build();
    }
}
