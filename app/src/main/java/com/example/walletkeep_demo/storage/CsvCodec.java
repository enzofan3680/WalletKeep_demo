package com.example.walletkeep_demo.storage;

import com.example.walletkeep_demo.model.Bill;
import com.example.walletkeep_demo.model.Category;
import com.example.walletkeep_demo.util.DateAmountUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 账单 CSV 编解码器（遵循 RFC 4180 转义与状态机解析）。
 */
public final class CsvCodec {
    public static final String HEADER = "id,amount,type,category,note,bill_date,receipt_path,created_at";

    private CsvCodec() {
    }

    public static String formatBills(List<Bill> bills) {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append("\n");
        if (bills != null) {
            for (Bill b : bills) {
                if (b == null) {
                    continue;
                }
                sb.append(b.getId()).append(",")
                        .append(String.format(Locale.US, "%.2f", b.getAmount())).append(",")
                        .append(b.getType()).append(",")
                        .append(escapeField(b.getCategoryName())).append(",")
                        .append(escapeField(b.getNote())).append(",")
                        .append(escapeField(b.getBillDate())).append(",")
                        .append(escapeField(b.getReceiptPath())).append(",")
                        .append(escapeField(b.getCreatedAt())).append("\n");
            }
        }
        return sb.toString();
    }

    public static List<Bill> parseBills(String csvContent) {
        List<Bill> result = new ArrayList<Bill>();
        if (csvContent == null) {
            return result;
        }
        String normalized = csvContent.startsWith("\uFEFF") ? csvContent.substring(1) : csvContent;
        if (normalized.trim().isEmpty()) {
            return result;
        }
        List<List<String>> rows = parseRows(normalized);
        if (rows.isEmpty()) {
            return result;
        }
        int startIndex = 0;
        List<String> firstRow = rows.get(0);
        if (!firstRow.isEmpty() && "id".equalsIgnoreCase(firstRow.get(0).trim().replace("\uFEFF", ""))) {
            startIndex = 1;
        }
        for (int i = startIndex; i < rows.size(); i++) {
            List<String> cols = rows.get(i);
            if (cols.size() == 1 && cols.get(0).trim().isEmpty()) {
                continue;
            }
            int rowNum = i + 1;
            if (cols.size() < 6 || cols.size() > 8) {
                throw new IllegalArgumentException("第 " + rowNum + " 行字段数量异常（共 " + cols.size() + " 列）");
            }
            double amount = DateAmountUtils.parsePositiveAmount(cols.get(1));
            if (amount <= 0) {
                throw new IllegalArgumentException("第 " + rowNum + " 行金额不合法: " + cols.get(1));
            }
            int type;
            try {
                type = Integer.parseInt(cols.get(2).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("第 " + rowNum + " 行收支类型不合法: " + cols.get(2));
            }
            if (type != Category.TYPE_EXPENSE && type != Category.TYPE_INCOME) {
                throw new IllegalArgumentException("第 " + rowNum + " 行收支类型必须为 0 或 1");
            }
            String categoryName = cols.get(3).trim();
            if (categoryName.isEmpty()) {
                categoryName = (type == Category.TYPE_EXPENSE) ? "其他支出" : "其他收入";
            }
            String note = cols.get(4);
            String billDate = cols.get(5).trim();
            if (billDate.isEmpty()) {
                billDate = DateAmountUtils.getCurrentDate();
            } else if (!DateAmountUtils.isValidDate(billDate)) {
                throw new IllegalArgumentException("第 " + rowNum + " 行日期格式不合法: " + billDate);
            }
            String receiptPath = cols.size() > 6 ? cols.get(6).trim() : "";
            String createdAt = cols.size() > 7 && !cols.get(7).trim().isEmpty()
                    ? cols.get(7).trim()
                    : DateAmountUtils.getCurrentDateTime();

            Bill bill = new Bill();
            bill.setAmount(amount);
            bill.setType(type);
            bill.setCategoryName(categoryName);
            bill.setNote(note);
            bill.setBillDate(billDate);
            bill.setReceiptPath(receiptPath);
            bill.setCreatedAt(createdAt);
            result.add(bill);
        }
        return result;
    }

    private static String escapeField(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        if (!needsQuotes) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static List<List<String>> parseRows(String content) {
        List<List<String>> rows = new ArrayList<List<String>>();
        List<String> currentRow = new ArrayList<String>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        boolean justClosedQuote = false;
        int len = content.length();
        for (int i = 0; i < len; i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && content.charAt(i + 1) == '"') {
                        currentField.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                        justClosedQuote = true;
                    }
                } else {
                    currentField.append(c);
                }
            } else if (justClosedQuote) {
                if (c == ',') {
                    currentRow.add(currentField.toString());
                    currentField.setLength(0);
                    fieldStarted = false;
                    justClosedQuote = false;
                } else if (c == '\r') {
                    // ignore CR, expect LF or EOF
                } else if (c == '\n') {
                    currentRow.add(currentField.toString());
                    currentField.setLength(0);
                    rows.add(currentRow);
                    currentRow = new ArrayList<String>();
                    fieldStarted = false;
                    justClosedQuote = false;
                } else {
                    throw new IllegalArgumentException("CSV 格式损坏：闭合引号后出现非法字符");
                }
            } else {
                if (c == '"') {
                    if (fieldStarted) {
                        throw new IllegalArgumentException("CSV 格式损坏：未加引号字段中出现非法双引号");
                    }
                    inQuotes = true;
                    fieldStarted = true;
                } else if (c == ',') {
                    currentRow.add(currentField.toString());
                    currentField.setLength(0);
                    fieldStarted = false;
                } else if (c == '\r') {
                    // ignore CR, handle LF
                } else if (c == '\n') {
                    currentRow.add(currentField.toString());
                    currentField.setLength(0);
                    rows.add(currentRow);
                    currentRow = new ArrayList<String>();
                    fieldStarted = false;
                } else {
                    currentField.append(c);
                    fieldStarted = true;
                }
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("CSV 格式损坏：存在未闭合的双引号");
        }
        if (currentField.length() > 0 || !currentRow.isEmpty() || justClosedQuote) {
            currentRow.add(currentField.toString());
            rows.add(currentRow);
        }
        return rows;
    }
}
