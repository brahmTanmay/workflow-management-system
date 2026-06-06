package com.wfm.leave.service;

import com.wfm.leave.model.Employee;
import com.wfm.leave.model.LeavePolicy;
import com.wfm.leave.model.enums.CycleType;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Utility to compute per-employee leave cycle boundaries.
 *
 * The cycle depends on the policy's CycleType and the employee's start date.
 */
public class LeaveCycleCalculator {

    private LeaveCycleCalculator() {}

    /**
     * Returns the start of the current leave cycle for this employee under this policy.
     */
    public static LocalDate currentCycleStart(Employee employee, LeavePolicy policy, LocalDate asOf) {
        return switch (policy.getCycleType()) {
            case CALENDAR_YEAR -> LocalDate.of(asOf.getYear(), 1, 1);

            case EMPLOYEE_START_DATE -> {
                LocalDate start = employee.getStartDate();
                // Find the most recent anniversary on or before asOf
                LocalDate anniversary = start.withYear(asOf.getYear());
                if (anniversary.isAfter(asOf)) {
                    anniversary = anniversary.minusYears(1);
                }
                yield anniversary;
            }

            case CUSTOM -> {
                int startMonth = policy.getCustomCycleStartMonth() != null
                        ? policy.getCustomCycleStartMonth() : 1;
                LocalDate cycleStart = LocalDate.of(asOf.getYear(), startMonth, 1);
                if (cycleStart.isAfter(asOf)) {
                    cycleStart = cycleStart.minusYears(1);
                }
                yield cycleStart;
            }
        };
    }

    /**
     * Returns the end of the current leave cycle (inclusive).
     */
    public static LocalDate currentCycleEnd(Employee employee, LeavePolicy policy, LocalDate asOf) {
        return currentCycleStart(employee, policy, asOf).plusYears(1).minusDays(1);
    }

    /**
     * How many complete years of tenure the employee has as of the given date.
     */
    public static long tenureYears(Employee employee, LocalDate asOf) {
        return ChronoUnit.YEARS.between(employee.getStartDate(), asOf);
    }

    /**
     * How many complete months into the current cycle we are (0-indexed).
     * Used for monthly accrual calculations.
     */
    public static int monthsIntoCycle(Employee employee, LeavePolicy policy, LocalDate asOf) {
        LocalDate cycleStart = currentCycleStart(employee, policy, asOf);
        return (int) ChronoUnit.MONTHS.between(cycleStart, asOf.plusDays(1));
    }
}
