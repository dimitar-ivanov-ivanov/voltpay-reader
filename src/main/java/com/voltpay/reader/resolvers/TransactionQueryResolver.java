package com.voltpay.reader.resolvers;

import com.voltpay.reader.entities.Transaction;
import com.voltpay.reader.repositories.TransactionRepository;
import lombok.AllArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@AllArgsConstructor
public class TransactionQueryResolver {

    private final TransactionRepository transactionRepository;

    @QueryMapping
    public List<Transaction> transactionByCustId(@Argument Long custId) {
        return transactionRepository.findByCustId(custId);
    }
}
