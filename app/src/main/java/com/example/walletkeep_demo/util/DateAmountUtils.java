package com.example.walletkeep_demo.util;

import com.example.walletkeep_demo.model.Category;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 日期与金额格式化/校验工具类（兼容 Android API 17+）。
 */
public final class DateAmountUtils {
    private DateAmountUtils() {
    }

    public static String getCurrentDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
    }

    public static String getCurrentMonth() {
        return new SimpleDateFormat("yyyy-MM", Locale.CHINA).format(new Date());
    }

    public static String getCurrentDateTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
    }

    public static String shiftMonth(String yyyyMM, int deltaMonths) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM", Locale.CHINA);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        if (yyyyMM != null && !yyyyMM.trim().isEmpty()) {
            try {
                Date date = fmt.parse(yyyyMM.trim());
                if (date != null) {
                    cal.setTime(date);
                    cal.set(Calendar.DAY_OF_MONTH, 1);
                }
            } catch (ParseException ignored) {
            }
        }
        cal.add(Calendar.MONTH, deltaMonths);
        return fmt.format(cal.getTime());
    }

    public static String formatAmount(String currency, double amount) {
        String symbol = (currency == null || currency.isEmpty()) ? "¥" : currency;
        return String.format(Locale.CHINA, "%s%.2f", symbol, amount);
    }

    public static String formatSignedAmount(String currency, int type, double amount) {
        String prefix = (type == Category.TYPE_INCOME) ? "+" : "-";
        return prefix + formatAmount(currency, amount);
    }

    public static double parsePositiveAmount(String raw) {
        if (raw == null) {
            return -1.0;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return -1.0;
        }
        try {
            double val = Double.parseDouble(trimmed);
            if (Double.isNaN(val) || Double.isInfinite(val) || val <= 0.0) {
                return -1.0;
            }
            double rounded = Math.round(val * 100.0) / 100.0;
            return rounded > 0.0 ? rounded : -1.0;
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }
}
