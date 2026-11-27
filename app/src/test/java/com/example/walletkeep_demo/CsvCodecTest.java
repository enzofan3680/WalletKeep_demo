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

    @Test
    public void parseBills_rejectsUnclosedQuoteAndTrailingGarbage() {
        String unclosedQuote = CsvCodec.HEADER + "\n1,38.50,0,餐饮,\"未闭合的备注,2026-11-24,,2026-11-24 12:00:00";
        try {
            CsvCodec.parseBills(unclosedQuote);
            fail("Expected IllegalArgumentException for unclosed quote");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("引号"));
        }

        String trailingCharsAfterQuote = CsvCodec.HEADER + "\n1,38.50,0,餐饮,\"备注\"多出字符,2026-11-24,,2026-11-24 12:00:00\n";
        try {
            CsvCodec.parseBills(trailingCharsAfterQuote);
            fail("Expected IllegalArgumentException for chars after closing quote");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("引号"));
        }
    }

    @Test
    public void parseBills_rejectsTooManyColumnsAndInvalidDate() {
        String tooManyCols = CsvCodec.HEADER + "\n1,10.00,0,餐饮,午饭,2026-11-24,,2026-11-24 12:00:00,extra_col\n";
        try {
            CsvCodec.parseBills(tooManyCols);
            fail("Expected IllegalArgumentException for >8 columns");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("2"));
        }

        String badDate = CsvCodec.HEADER + "\n1,10.00,0,餐饮,午饭,2026-02-30,,2026-11-24 12:00:00\n";
        try {
            CsvCodec.parseBills(badDate);
            fail("Expected IllegalArgumentException for invalid date");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("2"));
        }
    }

    @Test
    public void parseBills_toleratesWhitespaceAfterClosingQuote() {
        String csv = CsvCodec.HEADER
                + "\n1,10.00,0,餐饮,\"备注\" ,2026-11-24,,2026-11-24 12:00:00\n";
        List<Bill> parsed = CsvCodec.parseBills(csv);
        assertEquals(1, parsed.size());
        assertEquals("备注", parsed.get(0).getNote());
    }

    @Test
    public void parseBills_rejectsReorderedHeader() {
        String badHeader = "id,type,amount,category,note,bill_date,receipt_path,created_at\n"
                + "1,10.00,0,餐饮,午饭,2026-11-24,,\n";
        try {
            CsvCodec.parseBills(badHeader);
            fail("Expected IllegalArgumentException for reordered header");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("表头"));
        }
    }

    @Test
    public void parseBills_rejectsOversizedField() {
        StringBuilder huge = new StringBuilder(CsvCodec.MAX_FIELD_CHARS + 1);
        for (int i = 0; i <= CsvCodec.MAX_FIELD_CHARS; i++) {
            huge.append('x');
        }
        String csv = CsvCodec.HEADER + "\n1,10.00,0,餐饮," + huge
                + ",2026-11-24,,2026-11-24 12:00:00\n";
        try {
            CsvCodec.parseBills(csv);
            fail("Expected IllegalArgumentException for oversized field");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("字段长度"));
        }
    }

    @Test
    public void parseBills_rejectsAmountAboveMax() {
        String csv = CsvCodec.HEADER + "\n1,1e18,0,餐饮,x,2026-11-24,,2026-11-24 12:00:00\n";
        try {
            CsvCodec.parseBills(csv);
            fail("Expected IllegalArgumentException for oversized amount");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("金额"));
        }
    }

    @Test
    public void parseBills_rejectsTooManyRows() {
        StringBuilder csv = new StringBuilder(CsvCodec.HEADER).append('\n');
        for (int i = 0; i <= CsvCodec.MAX_ROWS; i++) {
            csv.append("1,10.00,0,餐饮,x,2026-11-24,,\n");
        }
        try {
            CsvCodec.parseBills(csv.toString());
            fail("Expected IllegalArgumentException for too many rows");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("行数"));
        }
    }
}
