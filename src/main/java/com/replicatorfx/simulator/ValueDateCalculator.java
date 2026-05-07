package com.replicatorfx.simulator;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class ValueDateCalculator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private ValueDateCalculator() {}

    public static String spotValueDate() {
        return addBusinessDays(LocalDate.now(), 2).format(FMT);
    }

    private static LocalDate addBusinessDays(LocalDate from, int businessDays) {
        LocalDate date = from;
        int remaining  = businessDays;
        while (remaining > 0) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY
                    && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                remaining--;
            }
        }
        return date;
    }
}
