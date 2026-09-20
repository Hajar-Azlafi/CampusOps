package com.campusops.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates calendar columns needed by newer application versions. */
@Component
@RequiredArgsConstructor
@Order(15)
public class NonWorkingDaySchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        jdbcTemplate.execute(
                "ALTER TABLE non_working_days "
                        + "ADD COLUMN IF NOT EXISTS recurrent boolean NOT NULL DEFAULT false");
    }
}