package com.example.walletkeep_demo;

import com.example.walletkeep_demo.model.Bill;
import com.example.walletkeep_demo.model.Category;
import com.example.walletkeep_demo.storage.CsvCodec;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CsvCodecTest {

    @Test
    public void roundTrip_preservesCommasQuotesNewlinesAndEmptyFields() {
        Bill b1 = new Bill();
        b1.setId(1);
        b1.setAmount(38.50);
        b1.setType(Category.TYPE_EXPENSE);
        b1.setCategoryName("餐饮");
        b1.setNote("牛肉面, 加蛋 \"特辣\"\n打包");
        b1.setBillDate("2026-11-24");
        b1.setReceiptPath("/data/user/0/app/files/receipts/IMG_1.jpg");
        b1.setCreatedAt("2026-11-24 12:30:00");

        Bill b2 = new Bill();
        b2.setId(2);
        b2.setAmount(5000.00);
        b2.setType(Category.TYPE_INCOME);
        b2.setCategoryName("工资");
        b2.setNote("");
        b2.setBillDate("2026-11-01");
        b2.setReceiptPath("");
        b2.setCreatedAt("2026-11-01 09:00:00");

        String csv = CsvCodec.formatBills(Arrays.asList(b1, b2));
        assertTrue(csv.startsWith(CsvCodec.HEADER + "\n"));

        List<Bill> parsed = CsvCodec.parseBills(csv);
        assertEquals(2, parsed.size());

        assertEquals(38.50, parsed.get(0).getAmount(), 0.001);
        assertEquals(Category.TYPE_EXPENSE, parsed.get(0).getType());
        assertEquals("餐饮", parsed.get(0).getCategoryName());
        assertEquals("牛肉面, 加蛋 \"特辣\"\n打包", parsed.get(0).getNote());
        assertEquals("2026-11-24", parsed.get(0).getBillDate());
        assertEquals("/data/user/0/app/files/receipts/IMG_1.jpg", parsed.get(0).getReceiptPath());
        assertEquals("2026-11-24 12:30:00", parsed.get(0).getCreatedAt());

        assertEquals(5000.00, parsed.get(1).getAmount(), 0.001);
        assertEquals(Category.TYPE_INCOME, parsed.get(1).getType());
        assertEquals("工资", parsed.get(1).getCategoryName());
        assertEquals("", parsed.get(1).getNote());
        assertEquals("2026-11-01", parsed.get(1).getBillDate());
        assertEquals("", parsed.get(1).getReceiptPath());
    }

    @Test
    public void parseBills_stripsBomIgnoresBlankLinesAndAppliesDefaults() {
        String csv = "\uFEFF" + CsvCodec.HEADER + "\r\n"
                + "\r\n"
                + "10,18.00,0,,,\r\n"
                + "11,200.00,1,,奖金,\r\n";

        List<Bill> parsed = CsvCodec.parseBills(csv);
        assertEquals(2, parsed.size());

        assertEquals("其他支出", parsed.get(0).getCategoryName());
        assertTrue(parsed.get(0).getBillDate().matches("\\d{4}-\\d{2}-\\d{2}"));
        assertTrue(parsed.get(0).getCreatedAt().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));

        assertEquals("其他收入", parsed.get(1).getCategoryName());
        assertEquals("奖金", parsed.get(1).getNote());
    }

    @Test
    public void parseBills_rejectsInvalidAmountRowWithOneBasedRowNumber() {
        String badCsv = CsvCodec.HEADER + "\n1,not_a_number,0,餐饮,午饭,2026-11-24,,2026-11-24 12:00:00\n";
        try {
            CsvCodec.parseBills(badCsv);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("2"));
        }
    }

    @Test
    public void parseBills_rejectsTooFewColumnsAndInvalidType() {
        String tooFewCols = CsvCodec.HEADER + "\n1,10.00,0,餐饮,午饭\n";
        try {
            CsvCodec.parseBills(tooFewCols);
            fail("Expected IllegalArgumentException for <6 columns");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("2"));
        }

        String badType = CsvCodec.HEADER + "\n1,10.00,2,餐饮,午饭,2026-11-24\n";
        try {
            CsvCodec.parseBills(badType);
            fail("Expected IllegalArgumentException for invalid type");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("2"));
        }
    }
}
