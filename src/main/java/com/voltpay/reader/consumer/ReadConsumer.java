package com.voltpay.reader.consumer;

import com.voltpay.reader.entities.Transaction;
import com.voltpay.reader.pojo.ReadEvent;
import com.voltpay.reader.repositories.TransactionRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ReadConsumer {

    private TransactionRepository transactionRepository;

    @Transactional
    @KafkaListener(topics = "read-topic", containerFactory = "kafkaListenerContainerFactory")
    public void processBatchOfMessages(List<ConsumerRecord<String, ReadEvent>> records) {

        for(ConsumerRecord<String, ReadEvent> record : records) {
            if (!isValid(record)) {
                continue;
            }
            ReadEvent event = record.value();
            Transaction transaction = new Transaction(event.getId(), event.getCreatedAt(), event.getUpdatedAt(), event.getAmount(), event.getStatus(),
                event.getCurrency(), event.getCustId(), event.getType(), event.getComment(), event.getVersion());

            try {
                transactionRepository.save(transaction);
            } catch (Exception ex) {
                // send to dead letter
            }
        }
    }

    private boolean isValid(ConsumerRecord<String, ReadEvent> record) {
        String key = record.key();
        Object value = record.value();

        // Disregard warmup events
        if (key == null || value == null || value.getClass() != ReadEvent.class) {
            return false;
        }

        ReadEvent event = (ReadEvent) value;

        if (event.getId() == null ||
            event.getAmount() == null ||
            event.getCreatedAt() == null ||
            event.getCurrency() == null ||
            event.getCustId() == null) {
            return false;
        }

        return true;
    }
}
