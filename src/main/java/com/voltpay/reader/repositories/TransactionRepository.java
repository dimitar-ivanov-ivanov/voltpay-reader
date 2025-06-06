package com.voltpay.reader.repositories;

import com.voltpay.reader.entities.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {


    List<Transaction> findByCustId(Long custId);
}
