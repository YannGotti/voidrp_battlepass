package ru.voidrp.battlepass.season;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class Season {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private Season() {}

    /** Returns the current season key, e.g. "2026-05". */
    public static String currentKey() {
        return LocalDate.now().format(FMT);
    }

    /** Returns how many days remain until the 1st of next month (inclusive of today). */
    public static int daysUntilReset() {
        LocalDate today = LocalDate.now();
        LocalDate firstNext = today.withDayOfMonth(1).plusMonths(1);
        return (int) (firstNext.toEpochDay() - today.toEpochDay());
    }
}
