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

    @KafkaListener(topics = "read-dlt", containerFactory = "deadLetterListenerContainerFactory")
    public void reprocessMessages(ReadEvent event) {
        try {
            kafkaTemplate.send("read-topic", event.getCustId().toString(), event);
            log.info("Sucessfully republished message {}", event.getMessageId());
        } catch (Exception ex) {
            log.error("Failed to produce message {} for reprocessing", event.getMessageId());
            throw ex;
        }
    }
}
