package com.hrportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hrportal.service.SalaryCalculator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** The salary breakdown on its own: no Spring, no database. */
class SalaryCalculatorTest {

    private static SalaryCalculator.Breakdown calc(String salary, String paidDays, int daysInMonth) {
        return SalaryCalculator.calculate(new BigDecimal(salary), new BigDecimal(paidDays), daysInMonth);
    }

    @Test
    void fullMonthSplitsIntoBasicHraAndSpecial() {
        var b = calc("100000", "30", 30);
        assertThat(b.grossEarned()).isEqualByComparingTo("100000");
        assertThat(b.basic()).isEqualByComparingTo("50000");
        assertThat(b.hra()).isEqualByComparingTo("20000");
        assertThat(b.special()).isEqualByComparingTo("30000");
    }

    @Test
    void providentFundIsCappedAtTheWageCeiling() {
        assertThat(calc("100000", "30", 30).providentFund()).isEqualByComparingTo("1800"); // 12% of 15,000
        assertThat(calc("20000", "30", 30).providentFund()).isEqualByComparingTo("1200");  // 12% of 10,000
    }

    @Test
    void professionalTaxOnlyFromFifteenThousand() {
        assertThat(calc("15000", "30", 30).professionalTax()).isEqualByComparingTo("200");
        assertThat(calc("14999", "30", 30).professionalTax()).isEqualByComparingTo("0");
    }

    @Test
    void netPayIsGrossMinusDeductions() {
        var b = calc("100000", "30", 30);
        assertThat(b.totalDeductions()).isEqualByComparingTo("2000");
        assertThat(b.netPay()).isEqualByComparingTo("98000");
        var small = calc("10000", "30", 30);
        assertThat(small.netPay()).isEqualByComparingTo("9400"); // PF 600, no professional tax
    }

    @Test
    void lossOfPayProRatesOverCalendarDays() {
        var b = calc("100000", "29", 31);
        assertThat(b.grossEarned()).isEqualByComparingTo("93548.39");
        assertThat(b.basic().add(b.hra()).add(b.special())).isEqualByComparingTo(b.grossEarned());
    }

    @Test
    void zeroPaidDaysMeansNothingToPay() {
        var b = calc("100000", "0", 28);
        assertThat(b.grossEarned()).isEqualByComparingTo("0");
        assertThat(b.netPay()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsImpossibleInput() {
        assertThatThrownBy(() -> calc("100000", "32", 31)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calc("-1", "30", 30)).isInstanceOf(IllegalArgumentException.class);
    }
}
