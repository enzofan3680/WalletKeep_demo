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
    /**
     * 单笔金额上限：¥999,999,999.99。防止 toCents 饱和与 SQLite SUM 64 位整数溢出。
     */
    public static final long MAX_AMOUNT_CENTS = 99_999_999_999L;
    public static final double MAX_AMOUNT_YUAN = MAX_AMOUNT_CENTS / 100.0;

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
        fmt.setLenient(false);
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

    public static boolean isValidDate(String yyyyMMdd) {
        if (yyyyMMdd == null) {
            return false;
        }
        String trimmed = yyyyMMdd.trim();
        if (!trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return false;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        fmt.setLenient(false);
        try {
            return fmt.parse(trimmed) != null;
        } catch (ParseException e) {
            return false;
        }
    }

    public static long toCents(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            return 0L;
        }
        double cents = amount * 100.0;
        if (cents >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        if (cents <= Long.MIN_VALUE) {
            return Long.MIN_VALUE;
        }
        return Math.round(cents);
    }

    public static boolean isAmountWithinRange(long cents) {
        return cents > 0L && cents <= MAX_AMOUNT_CENTS;
    }

    public static String formatCents(String currency, long cents) {
        String symbol = (currency == null || currency.isEmpty()) ? "¥" : currency;
        boolean negative = cents < 0L;
        long absCents = negative ? (cents == Long.MIN_VALUE ? Long.MAX_VALUE : -cents) : cents;
        long yuan = absCents / 100L;
        long fen = absCents % 100L;
        return String.format(Locale.CHINA, "%s%s%d.%02d", negative ? "-" : "", symbol, yuan, fen);
    }

    public static BudgetStatus evaluateBudget(double monthlyBudget, double monthExpense) {
        return evaluateBudgetCents(toCents(monthlyBudget), monthExpense);
    }

    public static BudgetStatus evaluateBudgetCents(long budgetCents, double monthExpense) {
        long expenseCents = Math.max(0L, toCents(monthExpense));
        if (budgetCents <= 0L) {
            return new BudgetStatus(BudgetStatus.LEVEL_UNSET, 0L, expenseCents, 0L, 0L, 0, 0.0);
        }
        double usagePercent = (expenseCents * 100.0) / budgetCents;
        int progressPercent = (int) Math.min(100L, Math.max(0L, Math.round(usagePercent)));
        long remainingCents = budgetCents - expenseCents;
        if (expenseCents > budgetCents) {
            return new BudgetStatus(
                    BudgetStatus.LEVEL_OVER,
                    budgetCents,
                    expenseCents,
                    remainingCents,
                    expenseCents - budgetCents,
                    progressPercent,
                    usagePercent
            );
        }
        // 使用整数乘法判断 80% 预警边界（避免浮点精度偏差），同时防止极端数值溢出
        boolean isWarning = (expenseCents >= budgetCents)
                || (budgetCents <= Long.MAX_VALUE / 100L
                ? expenseCents * 100L >= budgetCents * 80L
                : usagePercent >= 80.0);
        int level = isWarning ? BudgetStatus.LEVEL_WARNING : BudgetStatus.LEVEL_NORMAL;
        return new BudgetStatus(
                level,
                budgetCents,
                expenseCents,
                remainingCents,
                0L,
                progressPercent,
                usagePercent
        );
    }

    public static final class BudgetStatus {
        public static final int LEVEL_UNSET = 0;
        public static final int LEVEL_NORMAL = 1;
        public static final int LEVEL_WARNING = 2;
        public static final int LEVEL_OVER = 3;

        public final int level;
        public final long budgetCents;
        public final long expenseCents;
        public final long remainingCents;
        public final long overCents;
        public final int progressPercent;
        public final double usagePercent;

        public BudgetStatus(int level, long budgetCents, long expenseCents, long remainingCents,
                            long overCents, int progressPercent, double usagePercent) {
            this.level = level;
            this.budgetCents = budgetCents;
            this.expenseCents = expenseCents;
            this.remainingCents = remainingCents;
            this.overCents = overCents;
            this.progressPercent = progressPercent;
            this.usagePercent = usagePercent;
        }
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
            if (Double.isNaN(val) || Double.isInfinite(val) || val <= 0.0 || val > MAX_AMOUNT_YUAN) {
                return -1.0;
            }
            double rounded = Math.round(val * 100.0) / 100.0;
            return (rounded > 0.0 && rounded <= MAX_AMOUNT_YUAN) ? rounded : -1.0;
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }
}
