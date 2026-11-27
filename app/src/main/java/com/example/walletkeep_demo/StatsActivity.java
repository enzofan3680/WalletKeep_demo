package com.example.walletkeep_demo;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.walletkeep_demo.db.BillDao;
import com.example.walletkeep_demo.model.Category;
import com.example.walletkeep_demo.model.CategoryStat;
import com.example.walletkeep_demo.storage.PrefsManager;
import com.example.walletkeep_demo.ui.adapter.CategoryStatAdapter;
import com.example.walletkeep_demo.ui.view.CategoryChartView;
import com.example.walletkeep_demo.util.DateAmountUtils;
import com.example.walletkeep_demo.util.UiUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 月度收支统计页面：支持月份切换、月度收支概览与预算消耗进度展示、
 * 支出/收入构成切换、自定义 2D 占比图表与分类明细排行列表。
 */
public class StatsActivity extends AppCompatActivity {

    private static final String STATE_MONTH = "state_month";
    private static final String STATE_TYPE = "state_type";

    private BillDao billDao;
    private PrefsManager prefsManager;

    private TextView btnPrevMonth;
    private TextView tvStatsMonth;
    private TextView btnNextMonth;
    private TextView tvStatsExpense;
    private TextView tvStatsIncome;
    private TextView tvStatsBalance;
    private TextView tvStatsBudgetStatus;
    private ProgressBar pbStatsBudget;
    private TextView tvStatsTabExpense;
    private TextView tvStatsTabIncome;
    private CategoryChartView chartCategory;
    private ListView lvCategoryStats;
    private TextView tvStatsEmpty;

    private CategoryStatAdapter statAdapter;

    private String currentMonth;
    private int currentType = Category.TYPE_EXPENSE;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private int loadToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_stats);
        }

        billDao = new BillDao(this);
        prefsManager = new PrefsManager(this);

        if (savedInstanceState != null) {
            currentMonth = savedInstanceState.getString(STATE_MONTH, DateAmountUtils.getCurrentMonth());
            currentType = savedInstanceState.getInt(STATE_TYPE, Category.TYPE_EXPENSE);
        } else {
            currentMonth = DateAmountUtils.getCurrentMonth();
            currentType = Category.TYPE_EXPENSE;
        }
        if (currentMonth == null || currentMonth.trim().isEmpty()) {
            currentMonth = DateAmountUtils.getCurrentMonth();
        }

        bindViews();
        setupAdapterAndListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStats();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_MONTH, currentMonth);
        outState.putInt(STATE_TYPE, currentType);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdown();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void bindViews() {
        btnPrevMonth = findViewById(R.id.btn_prev_month);
        tvStatsMonth = findViewById(R.id.tv_stats_month);
        btnNextMonth = findViewById(R.id.btn_next_month);
        tvStatsExpense = findViewById(R.id.tv_stats_expense);
        tvStatsIncome = findViewById(R.id.tv_stats_income);
        tvStatsBalance = findViewById(R.id.tv_stats_balance);
        tvStatsBudgetStatus = findViewById(R.id.tv_stats_budget_status);
        pbStatsBudget = findViewById(R.id.pb_stats_budget);
        tvStatsTabExpense = findViewById(R.id.tv_stats_tab_expense);
        tvStatsTabIncome = findViewById(R.id.tv_stats_tab_income);
        chartCategory = findViewById(R.id.chart_category);
        lvCategoryStats = findViewById(R.id.lv_category_stats);
        tvStatsEmpty = findViewById(R.id.tv_stats_empty);

        lvCategoryStats.setFocusable(false);
    }

    private void setupAdapterAndListeners() {
        statAdapter = new CategoryStatAdapter(
                this,
                new ArrayList<CategoryStat>(),
                prefsManager.getDefaultCurrency()
        );
        lvCategoryStats.setAdapter(statAdapter);

        btnPrevMonth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentMonth = DateAmountUtils.shiftMonth(currentMonth, -1);
                loadStats();
            }
        });

        btnNextMonth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentMonth = DateAmountUtils.shiftMonth(currentMonth, 1);
                loadStats();
            }
        });

        tvStatsTabExpense.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentType != Category.TYPE_EXPENSE) {
                    currentType = Category.TYPE_EXPENSE;
                    loadStats();
                }
            }
        });

        tvStatsTabIncome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentType != Category.TYPE_INCOME) {
                    currentType = Category.TYPE_INCOME;
                    loadStats();
                }
            }
        });
    }

    private void loadStats() {
        updateTypeTabs();
        final String month = currentMonth;
        final int type = currentType;
        final int token = ++loadToken;
        final String currency = prefsManager.getDefaultCurrency();
        final long monthlyBudgetCents = prefsManager.getMonthlyBudgetCents();

        tvStatsMonth.setText(UiUtils.formatMonthLabel(month));

        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final double monthExpense = billDao.getMonthlyTotal(month, Category.TYPE_EXPENSE);
                    final double monthIncome = billDao.getMonthlyTotal(month, Category.TYPE_INCOME);
                    final List<CategoryStat> stats = billDao.getCategoryStats(month, type);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (token != loadToken || isUiInvalid()) {
                                return;
                            }
                            applyStats(currency, monthlyBudgetCents, monthExpense, monthIncome, stats);
                        }
                    });
                } catch (final RuntimeException e) {
                    Log.e("StatsActivity", "loadStats failed", e);
                    postError(R.string.error_load_failed);
                }
            }
        });
    }

    private void applyStats(String currency, long monthlyBudgetCents, double monthExpense,
                            double monthIncome, List<CategoryStat> stats) {
        double monthBalance = monthIncome - monthExpense;
        if (Math.abs(monthBalance) < 0.005) {
            monthBalance = 0.0;
        }

        tvStatsExpense.setText(DateAmountUtils.formatAmount(currency, monthExpense));
        tvStatsIncome.setText(DateAmountUtils.formatAmount(currency, monthIncome));
        tvStatsBalance.setText(UiUtils.formatBalanceAmount(currency, monthBalance));

        updateBudgetSection(currency, monthlyBudgetCents, monthExpense);

        chartCategory.setStats(stats, currency);
        statAdapter.updateData(stats, currency);

        if (stats.isEmpty()) {
            lvCategoryStats.setVisibility(View.GONE);
            tvStatsEmpty.setVisibility(View.VISIBLE);
        } else {
            tvStatsEmpty.setVisibility(View.GONE);
            lvCategoryStats.setVisibility(View.VISIBLE);
            UiUtils.setListViewHeightBasedOnChildren(lvCategoryStats);
        }
    }

    private void updateBudgetSection(String currency, long monthlyBudgetCents, double monthExpense) {
        DateAmountUtils.BudgetStatus status = DateAmountUtils.evaluateBudgetCents(monthlyBudgetCents, monthExpense);
        if (status.level == DateAmountUtils.BudgetStatus.LEVEL_UNSET) {
            tvStatsBudgetStatus.setText(R.string.label_budget_unset);
            pbStatsBudget.setProgress(0);
            UiUtils.applyBudgetProgressColors(pbStatsBudget, Color.WHITE);
            return;
        }

        tvStatsBudgetStatus.setText(getString(
                R.string.fmt_stats_budget_usage,
                DateAmountUtils.formatCents(currency, status.budgetCents),
                status.usagePercent
        ));

        pbStatsBudget.setProgress(status.progressPercent);

        int progressColor;
        if (status.level == DateAmountUtils.BudgetStatus.LEVEL_OVER) {
            progressColor = ContextCompat.getColor(this, R.color.colorDanger);
        } else if (status.level == DateAmountUtils.BudgetStatus.LEVEL_WARNING) {
            progressColor = ContextCompat.getColor(this, R.color.colorWarning);
        } else {
            progressColor = ContextCompat.getColor(this, R.color.textOnPrimary);
        }
        UiUtils.applyBudgetProgressColors(pbStatsBudget, progressColor);
    }

    private void updateTypeTabs() {
        boolean isExpense = currentType == Category.TYPE_EXPENSE;
        int onPrimary = ContextCompat.getColor(this, R.color.textOnPrimary);
        int secondary = ContextCompat.getColor(this, R.color.textSecondary);

        tvStatsTabExpense.setBackgroundResource(isExpense ? R.drawable.bg_tab_selected : R.drawable.bg_tab_unselected);
        tvStatsTabExpense.setTextColor(isExpense ? onPrimary : secondary);

        tvStatsTabIncome.setBackgroundResource(isExpense ? R.drawable.bg_tab_unselected : R.drawable.bg_tab_selected);
        tvStatsTabIncome.setTextColor(isExpense ? secondary : onPrimary);
    }

    private void postError(final int messageRes) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isUiInvalid()) {
                    Toast.makeText(StatsActivity.this, messageRes, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private boolean isUiInvalid() {
        return isFinishing() || isDestroyed();
    }
}
