package com.voltpay.reader.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "idempotency", schema = "read")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Idempotency {

    @Id
    private String id;

    private LocalDate date;
}
