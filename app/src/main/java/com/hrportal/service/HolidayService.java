package com.hrportal.service;

import com.hrportal.domain.Holiday;
import com.hrportal.repository.HolidayRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public holiday calendar and the working-day calculation built on it. */
@Service
@Transactional
public class HolidayService {

    private final HolidayRepository holidays;
    private final AuditService audit;

    public HolidayService(HolidayRepository holidays, AuditService audit) {
        this.holidays = holidays;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Holiday> forYear(int year) {
        return holidays.findByDateBetweenOrderByDateAsc(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    @Transactional(readOnly = true)
    public List<Holiday> forRange(LocalDate from, LocalDate to) {
        return holidays.findByDateBetweenOrderByDateAsc(from, to);
    }

    public Holiday add(LocalDate date, String name) {
        if (date == null || name == null || name.isBlank()) {
            throw new BusinessRuleException("Holiday date and name are required");
        }
        if (holidays.existsByDate(date)) {
            throw new BusinessRuleException(date + " is already a holiday");
        }
        Holiday saved = holidays.save(new Holiday(date, name.trim()));
        audit.record("HOLIDAY_ADDED", "Holiday", saved.getId(), date + " " + saved.getName());
        return saved;
    }

    public void delete(Long id) {
        Holiday holiday = holidays.findById(id).orElseThrow(() -> new NotFoundException("Holiday not found"));
        holidays.delete(holiday);
        audit.record("HOLIDAY_REMOVED", "Holiday", id, holiday.getDate() + " " + holiday.getName());
    }

    /** Monday–Friday days in the inclusive range that are not public holidays. */
    @Transactional(readOnly = true)
    public BigDecimal workingDays(LocalDate start, LocalDate end) {
        Set<LocalDate> holidayDates = new HashSet<>();
        holidays.findByDateBetweenOrderByDateAsc(start, end).forEach(h -> holidayDates.add(h.getDate()));
        int days = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (isWeekday(d) && !holidayDates.contains(d)) {
                days++;
            }
        }
        return BigDecimal.valueOf(days);
    }

    @Transactional(readOnly = true)
    public boolean isWorkingDay(LocalDate day) {
        return isWeekday(day) && !holidays.existsByDate(day);
    }

    private static boolean isWeekday(LocalDate d) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}
