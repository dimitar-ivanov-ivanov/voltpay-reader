package com.voltpay.reader.config;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.GraphQLScalarType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class JavaTimeScalar {

    public static final GraphQLScalarType LocalDateTime = GraphQLScalarType.newScalar()
        .name("LocalDateTime")
        .description("A custom scalar for LocalDateTime")
        .coercing(new Coercing<LocalDateTime, String>() {
            @Override
            public String serialize(Object dataFetcherResult) {
                if (dataFetcherResult instanceof LocalDateTime) {
                    return ((LocalDateTime) dataFetcherResult).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
                return null;
            }

            @Override
            public LocalDateTime parseValue(Object input) {
                return java.time.LocalDateTime.parse(input.toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }

            @Override
            public LocalDateTime parseLiteral(Object input) {
                if (input instanceof StringValue) {
                    return java.time.LocalDateTime.parse(((StringValue) input).getValue(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
                return null;
            }
        })
        .build();
}