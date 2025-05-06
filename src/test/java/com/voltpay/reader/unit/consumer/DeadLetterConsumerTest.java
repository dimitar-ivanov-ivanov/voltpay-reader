package com.voltpay.reader.unit.consumer;

import com.voltpay.reader.consumer.DeadLetterConsumer;
import com.voltpay.reader.pojo.ReadEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeadLetterConsumerTest {

    private static final Long CUST_ID = 1L;

    private DeadLetterConsumer consumer;

    private KafkaTemplate<String, ReadEvent> kafkaTemplate;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        consumer = new DeadLetterConsumer(kafkaTemplate);
    }

    @Test
    public void given_validInput_when_reprocessMessages_then_republishSuccessfully() {
        // GIVEN
        ReadEvent readEvent = new ReadEvent();
        readEvent.setCustId(CUST_ID);
        // WHEN
        consumer.reprocessMessages(readEvent);
        // THEN
        verify(kafkaTemplate).send("read-topic", readEvent.getCustId().toString(), readEvent);
    }

    @Test
    public void given_exception_when_reprocessMessages_then_rethrow() {
        // GIVEN
        ReadEvent readEvent = new ReadEvent();
        readEvent.setCustId(CUST_ID);
        when(kafkaTemplate.send("read-topic", readEvent.getCustId().toString(), readEvent))
            .thenThrow(RuntimeException.class);
        // WHEN
        // THEN throw exception
        assertThrows(RuntimeException.class, () -> consumer.reprocessMessages(readEvent));
    }

}