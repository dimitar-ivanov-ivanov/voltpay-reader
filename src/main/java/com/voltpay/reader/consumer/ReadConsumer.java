package com.voltpay.reader.consumer;

import com.voltpay.reader.entities.Transaction;
import com.voltpay.reader.pojo.ReadEvent;
import com.voltpay.reader.repositories.IdempotencyRepository;
import com.voltpay.reader.repositories.TransactionRepository;
import com.voltpay.reader.utils.Currency;
import com.voltpay.reader.utils.TrnStatus;
import com.voltpay.reader.utils.TrnType;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
// If Dead Letter consumption is enabled the Read Consumer should be disabled otherwise we could end up in an infinite loop of
// Read Consumer -> publish to DLT -> DLT publish to Read Consumer -> continue until we run out of memory
@ConditionalOnProperty(name = "kafka.dlt.enabled", havingValue = "false")
public class ReadConsumer {

    private static final List<String> CURRENCIES = Arrays.stream(Currency.values()).map(Enum::toString).toList();

    private static final List<Integer> STATUS_VALUES = Arrays.stream(TrnStatus.values()).map(TrnStatus::getValue).toList();

    private static final List<String> TYPES = Arrays.stream(TrnType.values()).map(Enum::toString).toList();

    private TransactionRepository transactionRepository;

    private IdempotencyRepository idempotencyRepository;

    @Transactional
    @KafkaListener(topics = "read-topic", containerFactory = "kafkaListenerContainerFactory")
    public void processMessage(ReadEvent event) {
        if (!isValid(event)) {
            log.warn("Invalid event, won't process");
            return;
        }

        try {
            // throws an exception when trying to persist a duplicate record
            idempotencyRepository.insertNew(event.getMessageId(), event.getCreatedAt().toLocalDate());

            Transaction transaction = new Transaction(event.getId(), event.getCreatedAt(), event.getUpdatedAt(), event.getAmount(), event.getStatus(),
                event.getCurrency(), event.getCustId(), event.getType(), event.getComment(), event.getVersion());

            transactionRepository.save(transaction);
            log.info("Successfully persisted transaction {}", event.getId());
        } catch (Exception ex) {
            log.warn("Error while trying to persist transaction {}", event.getId());
            // Don't send to dead letter here as it will retry and publish the same event twice
            //deadLetterTemplate.send("read-dlt", event.getCustId().toString(), event);

            // Rethrow exception to trigger transaction rollback.
            // If we skip this we will persist idempotency for events that we DIDN'T process
            throw ex;
        }
    }

    private boolean isValid(ReadEvent event) {
        // Disregard warmup events
        if (event == null || event.getMessageId() == null) {
            return false;
        }

        if (event.getId() == null ||
            event.getAmount() == null ||
            event.getCreatedAt() == null ||
            event.getCurrency() == null ||
            event.getCustId() == null ||
            event.getStatus() == null ||
            event.getType() == null ||
            !CURRENCIES.contains(event.getCurrency()) ||
            !STATUS_VALUES.contains(event.getStatus()) ||
            !TYPES.contains(event.getType())) {

            return false;
        }


        return true;
    }
}
