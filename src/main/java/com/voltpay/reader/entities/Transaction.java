package com.voltpay.reader.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction", schema = "read")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Transaction {

    @Id
    @JsonIgnore
    private String id;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private BigDecimal amount;

    private Integer status;

    private String currency;

    private Long custId;

    private String type;

    private String comment;

    private Integer version;
}
