package com.example.walletkeep_demo;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
        String currency = prefsManager.getDefaultCurrency();
        float monthlyBudget = prefsManager.getMonthlyBudget();

        tvStatsMonth.setText(formatMonthLabel(currentMonth));

        double monthExpense = billDao.getMonthlyTotal(currentMonth, Category.TYPE_EXPENSE);
        double monthIncome = billDao.getMonthlyTotal(currentMonth, Category.TYPE_INCOME);
        double monthBalance = monthIncome - monthExpense;

        tvStatsExpense.setText(DateAmountUtils.formatAmount(currency, monthExpense));
        tvStatsIncome.setText(DateAmountUtils.formatAmount(currency, monthIncome));
        tvStatsBalance.setText(formatBalanceAmount(currency, monthBalance));

        updateBudgetSection(currency, monthlyBudget, monthExpense);
        updateTypeTabs();

        List<CategoryStat> stats = billDao.getCategoryStats(currentMonth, currentType);
        chartCategory.setStats(stats, currency);
        statAdapter.updateData(stats, currency);

        if (stats.isEmpty()) {
            lvCategoryStats.setVisibility(View.GONE);
            tvStatsEmpty.setVisibility(View.VISIBLE);
        } else {
            tvStatsEmpty.setVisibility(View.GONE);
            lvCategoryStats.setVisibility(View.VISIBLE);
            setListViewHeightBasedOnChildren(lvCategoryStats);
        }
    }

    private void updateBudgetSection(String currency, float monthlyBudget, double monthExpense) {
        if (monthlyBudget <= 0f) {
            tvStatsBudgetStatus.setText("未设置月度预算");
            pbStatsBudget.setProgress(0);
            applyBudgetProgressColors(Color.WHITE);
            return;
        }

        double usagePct = (monthExpense * 100.0) / monthlyBudget;
        tvStatsBudgetStatus.setText(String.format(
                Locale.CHINA,
                "月度预算 %s · 已使用 %.1f%%",
                DateAmountUtils.formatAmount(currency, monthlyBudget),
                usagePct
        ));

        int progress = (int) Math.min(100L, Math.max(0L, Math.round(usagePct)));
        pbStatsBudget.setProgress(progress);

        int progressColor;
        if (usagePct >= 100.0) {
            progressColor = ContextCompat.getColor(this, R.color.colorDanger);
        } else if (usagePct >= 80.0) {
            progressColor = ContextCompat.getColor(this, R.color.colorWarning);
        } else {
            progressColor = ContextCompat.getColor(this, R.color.textOnPrimary);
        }
        applyBudgetProgressColors(progressColor);
    }

    private void applyBudgetProgressColors(int progressColor) {
        Drawable drawable = pbStatsBudget.getProgressDrawable();
        if (drawable == null) {
            return;
        }
        Drawable mutated = drawable.mutate();
        if (mutated instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) mutated;
            Drawable bgLayer = layers.findDrawableByLayerId(android.R.id.background);
            if (bgLayer != null) {
                bgLayer.setColorFilter(0x4DFFFFFF, PorterDuff.Mode.SRC_IN);
            }
            Drawable progressLayer = layers.findDrawableByLayerId(android.R.id.progress);
            if (progressLayer != null) {
                progressLayer.setColorFilter(progressColor, PorterDuff.Mode.SRC_IN);
            }
        } else {
            mutated.setColorFilter(progressColor, PorterDuff.Mode.SRC_IN);
        }
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

    private void setListViewHeightBasedOnChildren(final ListView listView) {
        ListAdapter adapter = listView.getAdapter();
        if (adapter == null) {
            return;
        }
        int count = adapter.getCount();
        ViewGroup.LayoutParams params = listView.getLayoutParams();
        if (count == 0) {
            params.height = 0;
            listView.setLayoutParams(params);
            return;
        }

        final int listWidth = listView.getWidth();
        int widthSpec;
        if (listWidth > 0) {
            int contentWidth = Math.max(1, listWidth - listView.getPaddingLeft() - listView.getPaddingRight());
            widthSpec = View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY);
        } else {
            widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        }
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

        int totalHeight = listView.getPaddingTop() + listView.getPaddingBottom();
        for (int i = 0; i < count; i++) {
            View itemView = adapter.getView(i, null, listView);
            if (itemView.getLayoutParams() == null) {
                itemView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }
            itemView.measure(widthSpec, heightSpec);
            totalHeight += itemView.getMeasuredHeight();
        }
        if (count > 1) {
            totalHeight += listView.getDividerHeight() * (count - 1);
        }

        params.height = totalHeight;
        listView.setLayoutParams(params);

        if (listWidth == 0) {
            listView.post(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing() && listView.getWidth() > 0) {
                        setListViewHeightBasedOnChildren(listView);
                    }
                }
            });
        }
    }

    private static String formatMonthLabel(String yyyyMM) {
        if (yyyyMM == null || yyyyMM.trim().isEmpty()) {
            return "";
        }
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM", Locale.CHINA);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy年MM月", Locale.CHINA);
        try {
            Date date = parser.parse(yyyyMM.trim());
            if (date != null) {
                return formatter.format(date);
            }
        } catch (ParseException ignored) {
        }
        return yyyyMM;
    }

    private static String formatBalanceAmount(String currency, double balance) {
        if (balance < 0.0) {
            return "-" + DateAmountUtils.formatAmount(currency, Math.abs(balance));
        }
        return DateAmountUtils.formatAmount(currency, balance);
    }
}
