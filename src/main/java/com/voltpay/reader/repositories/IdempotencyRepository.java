package com.voltpay.reader.repositories;

import com.voltpay.reader.entities.Idempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyRepository extends JpaRepository<Idempotency, String> {
}
