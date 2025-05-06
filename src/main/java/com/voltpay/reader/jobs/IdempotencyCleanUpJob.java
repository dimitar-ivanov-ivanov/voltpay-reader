package com.voltpay.reader.jobs;

import com.voltpay.reader.repositories.IdempotencyRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@AllArgsConstructor
public class IdempotencyCleanUpJob {

    private IdempotencyRepository idempotencyRepository;

    /**
     * Job to clean up old records for idempotency.
     * Runs once a week.
     * @return the amount of records we deleted
     */
    @Scheduled(cron = "0 0 0 * * 0")
    @Transactional
    public int cleanUp() {
        int deletedRecords = idempotencyRepository.deleteOldRecords(LocalDate.now().minusWeeks(1));
        log.info("Successfully cleaned up {}", deletedRecords);
        return deletedRecords;
    }
}
