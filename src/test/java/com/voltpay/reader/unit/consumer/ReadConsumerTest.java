package com.voltpay.reader.unit.consumer;

import com.voltpay.reader.consumer.ReadConsumer;
import com.voltpay.reader.entities.Transaction;
import com.voltpay.reader.pojo.ReadEvent;
import com.voltpay.reader.repositories.IdempotencyRepository;
import com.voltpay.reader.repositories.TransactionRepository;
import com.voltpay.reader.utils.Currency;
import com.voltpay.reader.utils.TrnStatus;
import com.voltpay.reader.utils.TrnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReadConsumerTest {

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

    private ReadConsumer readConsumer;

    private TransactionRepository transactionRepository;

    private IdempotencyRepository idempotencyRepository;

    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        idempotencyRepository = mock(IdempotencyRepository.class);
        transactionManager = mock(PlatformTransactionManager.class);
        readConsumer = new ReadConsumer(transactionRepository, idempotencyRepository, transactionManager);
    }

    @Test
    public void given_nullEvent_when_processMessage_then_disregardEvent() {
        // GIVEN
        // WHEN
        readConsumer.processMessage(null);
        // THEN
        verifyNoInteractions(idempotencyRepository);
        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(transactionManager);
    }

    @ParameterizedTest
    @MethodSource("invalidEvents")
    public void given_eventNoValid_when_processMessage_then_disregardEvent(ReadEvent event) {
        // GIVEN
        // WHEN
        readConsumer.processMessage(event);
        // THEN
        verifyNoInteractions(idempotencyRepository);
        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(transactionManager);
    }

    @Test
    public void given_exceptionOnIdempotency_when_processMessage_then_dontPersist() {
        // GIVEN
        ReadEvent event = buildReadEvent();
        TransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(status);
        doThrow(RuntimeException.class).when(idempotencyRepository).insertNew(event.getMessageId(), event.getCreatedAt().toLocalDate());
        // WHEN
        readConsumer.processMessage(event);
        // THEN
        verifyNoInteractions(transactionRepository);
        verify(transactionManager).rollback(status);
    }

    @Test
    public void given_validEvent_when_processMessage_then_process() {
        // GIVEN
        TransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(status);
        ReadEvent event = buildReadEvent();
        // WHEN
        readConsumer.processMessage(event);
        // THEN
        verify(idempotencyRepository).insertNew(event.getMessageId(), event.getCreatedAt().toLocalDate());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction trn = captor.getValue();

        assertEquals(event.getId(), trn.getId());
        assertEquals(event.getCreatedAt(), trn.getCreatedAt());
        assertEquals(event.getUpdatedAt(), trn.getUpdatedAt());
        assertEquals(event.getAmount(), trn.getAmount());
        assertEquals(event.getStatus(), trn.getStatus());
        assertEquals(event.getCurrency(), trn.getCurrency());
        assertEquals(event.getCustId(), trn.getCustId());
        assertEquals(event.getType(), trn.getType());
        assertEquals(event.getComment(), trn.getComment());
        assertEquals(event.getVersion(), trn.getVersion());

        verify(transactionManager).commit(status);
    }

    private static Stream<Arguments> invalidEvents() {
        return Stream.of(
            // null message id
            Arguments.of(ReadEvent.builder().build()),

            // null record id
            Arguments.of(ReadEvent.builder().messageId(MESSAGE_ID).build()),

            // null amount
            Arguments.of(ReadEvent.builder().messageId(MESSAGE_ID).id(RECORD_ID).build()),

            // null created at
            Arguments.of(ReadEvent.builder().messageId(MESSAGE_ID).id(RECORD_ID).amount(AMOUNT).build()),

            // null currency
            Arguments.of(ReadEvent.builder().messageId(MESSAGE_ID).id(RECORD_ID)
                .amount(AMOUNT).createdAt(CREATED_AT).build()),

            // null customer id
            Arguments.of(ReadEvent.builder().messageId(MESSAGE_ID).id(RECORD_ID).amount(AMOUNT)
                .createdAt(CREATED_AT).currency(CURRENCY).build()),

            // null status
            Arguments.of(ReadEvent.builder().messageId(MESSAGE_ID).id(RECORD_ID).amount(AMOUNT)
                .createdAt(CREATED_AT).currency(CURRENCY).custId(CUST_ID).build()),

            // null type
            Arguments.of(ReadEvent.builder().messageId(MESSAGE_ID).id(RECORD_ID).amount(AMOUNT)
                .createdAt(CREATED_AT).currency(CURRENCY).custId(CUST_ID).status(STATUS).build()),

            // invalid currency
            Arguments.of(ReadEvent.builder().messageId(MESSAGE_ID).id(RECORD_ID).amount(AMOUNT)
                .createdAt(CREATED_AT).currency("ABC").custId(CUST_ID).status(STATUS).type(TYPE).build()),

            // invalid status
            Arguments.of(ReadEvent.builder().messageId(MESSAGE_ID).id(RECORD_ID).amount(AMOUNT)
                .createdAt(CREATED_AT).currency(CURRENCY).custId(CUST_ID).status(-10).type(TYPE).build()),

            // invalid type
            Arguments.of(ReadEvent.builder().messageId(MESSAGE_ID).id(RECORD_ID).amount(AMOUNT)
                .createdAt(CREATED_AT).currency(CURRENCY).custId(CUST_ID).status(STATUS).type("DADA").build())
        );
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