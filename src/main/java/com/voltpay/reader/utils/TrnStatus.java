package com.voltpay.reader.utils;

import lombok.Getter;

@Getter
public enum TrnStatus {

    SUCCESS(2), FAIL(-2), PENDING(0);

    private Integer value;

    TrnStatus(Integer value) {
        this.value = value;
    }
}
