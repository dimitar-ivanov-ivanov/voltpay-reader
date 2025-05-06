package com.voltpay.reader.consumer;

import com.voltpay.reader.pojo.ReadEvent;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "kafka.dlt.enabled", havingValue = "true")
@AllArgsConstructor
@Slf4j
public class DeadLetterConsumer {

    @Autowired
    private KafkaTemplate<String, ReadEvent> kafkaTemplate;

    /**
     * Consumer for events in dead letter topic.
     * Republishes the events back to the main read topic.
     * Should be enabled only when we have found only the bug, which caused them to fail.
     * Otherwise the event will just be published right back to the DLT.
     *
     * @param event event from dead letter
     */
    @KafkaListener(topics = "read-dlt", containerFactory = "deadLetterListenerContainerFactory")
    public void reprocessMessages(ReadEvent event) {
        try {
            kafkaTemplate.send("read-topic", event.getCustId().toString(), event);
            log.info("Successfully republished message {}", event.getMessageId());
        } catch (Exception ex) {
            log.error("Failed to produce message {} for reprocessing", event.getMessageId());
            throw ex;
        }
    }
}
