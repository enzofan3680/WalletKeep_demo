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
}
