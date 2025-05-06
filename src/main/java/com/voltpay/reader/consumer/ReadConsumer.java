package com.voltpay.reader.consumer;

import com.voltpay.reader.entities.Transaction;
import com.voltpay.reader.pojo.ReadEvent;
import com.voltpay.reader.repositories.IdempotencyRepository;
import com.voltpay.reader.repositories.TransactionRepository;
import com.voltpay.reader.utils.Currency;
import com.voltpay.reader.utils.TrnStatus;
import com.voltpay.reader.utils.TrnType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

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

    private PlatformTransactionManager transactionManager;

    @KafkaListener(topics = "read-topic", containerFactory = "kafkaListenerContainerFactory")
    public void processMessage(ReadEvent event) {
        if (!isValid(event)) {
            log.warn("Invalid event, won't process");
            return;
        }

        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName("read-transaction");
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        TransactionStatus status = transactionManager.getTransaction(def);

        try {
            // throws an exception when trying to persist a duplicate record
            idempotencyRepository.insertNew(event.getMessageId(), event.getCreatedAt().toLocalDate());

            Transaction transaction = new Transaction(event.getId(), event.getCreatedAt(), event.getUpdatedAt(), event.getAmount(), event.getStatus(),
                event.getCurrency(), event.getCustId(), event.getType(), event.getComment(), event.getVersion());

            transactionRepository.save(transaction);
            transactionManager.commit(status);
            log.info("Successfully persisted transaction {}", event.getId());
        } catch (Exception ex) {
            log.warn("Error while trying to persist transaction {}", event.getId(), ex);
            // Don't send to dead letter here as it will retry and publish the same event twice
            //deadLetterTemplate.send("read-dlt", event.getCustId().toString(), event);

            transactionManager.rollback(status);
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
