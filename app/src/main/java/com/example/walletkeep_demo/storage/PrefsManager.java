package com.example.walletkeep_demo.storage;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * 应用设置 SharedPreferences 管理器。
 */
public class PrefsManager {
    public static final String PREF_NAME = "app_settings";

    public static final String KEY_MONTHLY_BUDGET = "monthly_budget";
    public static final String KEY_MONTHLY_BUDGET_CENTS = "monthly_budget_cents";
    public static final String KEY_DEFAULT_CURRENCY = "default_currency";
    public static final String KEY_THEME_MODE = "theme_mode";
    public static final String KEY_REMINDER_ENABLED = "reminder_enabled";
    public static final String KEY_FIRST_LAUNCH = "first_launch";

    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    public static final float DEFAULT_MONTHLY_BUDGET = 3000.0f;
    public static final long DEFAULT_MONTHLY_BUDGET_CENTS = 300000L;
    public static final String DEFAULT_CURRENCY = "¥";

    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        Context appContext = context.getApplicationContext();
        Context target = (appContext != null) ? appContext : context;
        this.prefs = target.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public long getMonthlyBudgetCents() {
        if (prefs.contains(KEY_MONTHLY_BUDGET_CENTS)) {
            long cents = prefs.getLong(KEY_MONTHLY_BUDGET_CENTS, DEFAULT_MONTHLY_BUDGET_CENTS);
            return Math.max(0L, cents);
        }
        float legacy = getMonthlyBudget();
        return Math.max(0L, Math.round((double) legacy * 100.0));
    }

    public void setMonthlyBudgetCents(long budgetCents) {
        long safeCents = Math.max(0L, budgetCents);
        prefs.edit()
                .putLong(KEY_MONTHLY_BUDGET_CENTS, safeCents)
                .putFloat(KEY_MONTHLY_BUDGET, (float) (safeCents / 100.0))
                .apply();
    }

    public float getMonthlyBudget() {
        if (prefs.contains(KEY_MONTHLY_BUDGET_CENTS)) {
            return (float) (getMonthlyBudgetCents() / 100.0);
        }
        float budget = prefs.getFloat(KEY_MONTHLY_BUDGET, DEFAULT_MONTHLY_BUDGET);
        return (Float.isNaN(budget) || Float.isInfinite(budget) || budget < 0f) ? 0f : budget;
    }

    public void setMonthlyBudget(float budget) {
        float safeBudget = (Float.isNaN(budget) || Float.isInfinite(budget) || budget < 0f) ? 0f : budget;
        long cents = Math.max(0L, Math.round((double) safeBudget * 100.0));
        prefs.edit()
                .putLong(KEY_MONTHLY_BUDGET_CENTS, cents)
                .putFloat(KEY_MONTHLY_BUDGET, safeBudget)
                .apply();
    }

    public String getDefaultCurrency() {
        String currency = prefs.getString(KEY_DEFAULT_CURRENCY, DEFAULT_CURRENCY);
        if (currency == null || currency.trim().isEmpty()) {
            return DEFAULT_CURRENCY;
        }
        return currency;
    }

    public void setDefaultCurrency(String currency) {
        String safeCurrency = (currency == null || currency.trim().isEmpty())
                ? DEFAULT_CURRENCY
                : currency.trim();
        prefs.edit().putString(KEY_DEFAULT_CURRENCY, safeCurrency).apply();
    }

    public String getThemeMode() {
        String mode = prefs.getString(KEY_THEME_MODE, THEME_LIGHT);
        return THEME_DARK.equals(mode) ? THEME_DARK : THEME_LIGHT;
    }

    public void setThemeMode(String mode) {
        String normalized = THEME_DARK.equals(mode) ? THEME_DARK : THEME_LIGHT;
        prefs.edit().putString(KEY_THEME_MODE, normalized).apply();
    }

    public boolean isDarkTheme() {
        return THEME_DARK.equals(getThemeMode());
    }

    public void applySavedTheme() {
        AppCompatDelegate.setDefaultNightMode(
                isDarkTheme() ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }

    public boolean isReminderEnabled() {
        return prefs.getBoolean(KEY_REMINDER_ENABLED, true);
    }

    public void setReminderEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply();
    }

    public boolean isFirstLaunch() {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true);
    }

    public void setFirstLaunch(boolean firstLaunch) {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, firstLaunch).apply();
    }
}
