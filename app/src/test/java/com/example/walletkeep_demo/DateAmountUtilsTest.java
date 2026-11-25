package com.example.walletkeep_demo;

import com.example.walletkeep_demo.model.Category;
import com.example.walletkeep_demo.util.DateAmountUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DateAmountUtilsTest {

    @Test
    public void currentDateTimeFormats_matchExpectedPatterns() {
        assertTrue(DateAmountUtils.getCurrentDate().matches("\\d{4}-\\d{2}-\\d{2}"));
        assertTrue(DateAmountUtils.getCurrentMonth().matches("\\d{4}-\\d{2}"));
        assertTrue(DateAmountUtils.getCurrentDateTime().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    public void shiftMonth_handlesYearRollover() {
        assertEquals("2026-12", DateAmountUtils.shiftMonth("2026-11", 1));
        assertEquals("2027-01", DateAmountUtils.shiftMonth("2026-12", 1));
        assertEquals("2025-12", DateAmountUtils.shiftMonth("2026-01", -1));
        assertEquals("2025-01", DateAmountUtils.shiftMonth("2026-01", -12));
    }

    @Test
    public void formatAmount_defaultsEmptyCurrencyAndFormatsTwoDecimals() {
        assertEquals("¥12.50", DateAmountUtils.formatAmount("¥", 12.5));
        assertEquals("¥100.00", DateAmountUtils.formatAmount(null, 100.0));
        assertEquals("¥0.50", DateAmountUtils.formatAmount("", 0.5));
        assertEquals("$50.25", DateAmountUtils.formatAmount("$", 50.25));
    }

    @Test
    public void formatSignedAmount_usesSignByType() {
        assertEquals("-¥88.00", DateAmountUtils.formatSignedAmount("¥", Category.TYPE_EXPENSE, 88.0));
        assertEquals("+$500.25", DateAmountUtils.formatSignedAmount("$", Category.TYPE_INCOME, 500.25));
        assertEquals("-¥9.90", DateAmountUtils.formatSignedAmount(null, Category.TYPE_EXPENSE, 9.9));
    }

    @Test
    public void parsePositiveAmount_validatesAndRounds() {
        assertEquals(25.5, DateAmountUtils.parsePositiveAmount("25.5"), 0.001);
        assertEquals(12.35, DateAmountUtils.parsePositiveAmount(" 12.345 "), 0.001);
        assertEquals(-1.0, DateAmountUtils.parsePositiveAmount(null), 0.001);
        assertEquals(-1.0, DateAmountUtils.parsePositiveAmount("   "), 0.001);
        assertEquals(-1.0, DateAmountUtils.parsePositiveAmount("0"), 0.001);
        assertEquals(-1.0, DateAmountUtils.parsePositiveAmount("-5"), 0.001);
        assertEquals(-1.0, DateAmountUtils.parsePositiveAmount("abc"), 0.001);
        assertEquals(-1.0, DateAmountUtils.parsePositiveAmount("NaN"), 0.001);
        assertEquals(-1.0, DateAmountUtils.parsePositiveAmount("Infinity"), 0.001);
    }

    @Test
    public void isValidDate_acceptsRealDatesAndRejectsMalformedDates() {
        assertTrue(DateAmountUtils.isValidDate("2026-11-24"));
        assertTrue(DateAmountUtils.isValidDate("2024-02-29"));
        assertEquals(false, DateAmountUtils.isValidDate("2026-02-29"));
        assertEquals(false, DateAmountUtils.isValidDate("2026-13-01"));
        assertEquals(false, DateAmountUtils.isValidDate("2026-11-32"));
        assertEquals(false, DateAmountUtils.isValidDate("not-a-date"));
        assertEquals(false, DateAmountUtils.isValidDate("2026/11/24"));
        assertEquals(false, DateAmountUtils.isValidDate(null));
    }

    @Test
    public void evaluateBudget_avoidsFloatPrecisionFalseOverBudget() {
        // 100.10f in IEEE-754 float is 100.0999984741211d; spending exact 100.10 must NOT trigger OVER
        float budgetFloat = 100.10f;
        DateAmountUtils.BudgetStatus exactStatus = DateAmountUtils.evaluateBudget(budgetFloat, 100.10d);
        assertEquals(DateAmountUtils.BudgetStatus.LEVEL_WARNING, exactStatus.level);
        assertEquals(0L, exactStatus.overCents);
        assertEquals(0L, exactStatus.remainingCents);
        assertEquals(100, exactStatus.progressPercent);

        // Summed float drift: 0.10 + 0.20 + 99.80 = 100.10000000000001 -> still 10010 cents, not over
        double driftedSum = 0.10 + 0.20 + 99.80;
        DateAmountUtils.BudgetStatus driftedStatus = DateAmountUtils.evaluateBudget(budgetFloat, driftedSum);
        assertEquals(DateAmountUtils.BudgetStatus.LEVEL_WARNING, driftedStatus.level);
        assertEquals(0L, driftedStatus.overCents);

        // Real 1-cent overspend MUST trigger OVER with exact 1 cent
        DateAmountUtils.BudgetStatus overStatus = DateAmountUtils.evaluateBudget(budgetFloat, 100.11d);
        assertEquals(DateAmountUtils.BudgetStatus.LEVEL_OVER, overStatus.level);
        assertEquals(1L, overStatus.overCents);
        assertEquals("¥0.01", DateAmountUtils.formatCents("¥", overStatus.overCents));

        // 80% warning boundary using cents
        DateAmountUtils.BudgetStatus warnStatus = DateAmountUtils.evaluateBudget(100.0f, 80.0d);
        assertEquals(DateAmountUtils.BudgetStatus.LEVEL_WARNING, warnStatus.level);
        DateAmountUtils.BudgetStatus normalStatus = DateAmountUtils.evaluateBudget(100.0f, 79.99d);
        assertEquals(DateAmountUtils.BudgetStatus.LEVEL_NORMAL, normalStatus.level);

        // Large budget above 2^17 where 32-bit float loses 1-cent precision (131072.01f -> 131072.015625)
        long largeBudgetCents = 13107201L;
        DateAmountUtils.BudgetStatus largeExact = DateAmountUtils.evaluateBudgetCents(largeBudgetCents, 131072.01d);
        assertEquals(DateAmountUtils.BudgetStatus.LEVEL_WARNING, largeExact.level);
        assertEquals(13107201L, largeExact.budgetCents);
        assertEquals(0L, largeExact.overCents);
        assertEquals("¥131072.01", DateAmountUtils.formatCents("¥", largeExact.budgetCents));

        DateAmountUtils.BudgetStatus largeOver = DateAmountUtils.evaluateBudgetCents(largeBudgetCents, 131072.02d);
        assertEquals(DateAmountUtils.BudgetStatus.LEVEL_OVER, largeOver.level);
        assertEquals(1L, largeOver.overCents);
        assertEquals("¥0.01", DateAmountUtils.formatCents("¥", largeOver.overCents));
    }
}
