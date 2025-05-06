package com.voltpay.reader.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.voltpay.reader.pojo.ReadEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.max-poll-records}")
    private Integer maxPollRecords;

    @Value("${spring.kafka.consumer.fetch-max-wait}")
    private Integer maxFetchWaitInMs;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.consumer.dlt-group-id}")
    private String dltGroupId;

    @Value("${spring.kafka.consumer.threads}")
    private Integer consumerThreads;

    @Value("${spring.kafka.consumer.deadLetterThreads}")
    private Integer deadLetterThreads;


    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, maxFetchWaitInMs);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // Done in order to avoid
        // The class 'com.voltpay.voltpay_writer.pojo.ReadEvent' is not in the trusted packages: [java.util, java.lang, java.lang.*]. If you believe this class is safe to deserialize, please provide its name.
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.TYPE_MAPPINGS,
            "com.voltpay.voltpay_writer.pojo.ReadEvent:com.voltpay.reader.pojo.ReadEvent");

        return new DefaultKafkaConsumerFactory<>(props,
            new StringDeserializer(),
            new JsonDeserializer<>(Object.class, objectMapper()));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ReadEvent> kafkaListenerContainerFactory(KafkaTemplate<String, ReadEvent> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, ReadEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setBatchListener(false);
        factory.setConcurrency(consumerThreads);

        factory.setCommonErrorHandler(errorHandler(kafkaTemplate));

        return factory;
    }

    /**
     * Error handler which publishes the event to dead letter after retrying once.
     * Retry occurs after 1 second of failure.
     *
     * @param template template for publishing
     * @return error handler
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, ReadEvent> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
            (record, exception) -> new TopicPartition("read-dlt", record.partition()));

        BackOff backOff = new FixedBackOff(1000L, 1);

        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean
    public ConsumerFactory<String, ReadEvent> deadLetterConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, maxFetchWaitInMs);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, dltGroupId);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        return new DefaultKafkaConsumerFactory<>(props,
            new StringDeserializer(),
            new JsonDeserializer<>(ReadEvent.class, objectMapper()));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ReadEvent> deadLetterListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ReadEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(deadLetterConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setConcurrency(deadLetterThreads); // Number of threads to process messages
        factory.setBatchListener(false);

        // Retry configuration
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            new FixedBackOff(500L, 1L) // 1 retry after 500ms
        );

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean
    public ProducerFactory<String, ReadEvent> readProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        return new DefaultKafkaProducerFactory<>(configProps,
            new StringSerializer(),
            new org.springframework.kafka.support.serializer.JsonSerializer<>(objectMapper()));
    }

    @Bean
    public KafkaTemplate<String, ReadEvent> readEventKafkaTemplate() {
        return new KafkaTemplate<>(readProducerFactory());
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.registerModule(javaTimeModule);
        return mapper;
    }
}
