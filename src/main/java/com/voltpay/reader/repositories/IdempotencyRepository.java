package com.voltpay.reader.repositories;

import com.voltpay.reader.entities.Idempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface IdempotencyRepository extends JpaRepository<Idempotency, String> {

    @Modifying
    @Query(
        value = "INSERT INTO read.idempotency (id, date) VALUES (:id, :date)",
        nativeQuery = true
    )
    void insertNew(@Param("id") String id, @Param("date") LocalDate date);

    @Modifying
    @Query(
        value = "DELETE FROM read.idempotency where date <= :date",
        nativeQuery = true
    )
    int deleteOldRecords(LocalDate date);

}
