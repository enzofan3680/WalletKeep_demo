package com.example.walletkeep_demo;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.example.walletkeep_demo.db.BillDao;
import com.example.walletkeep_demo.model.Bill;
import com.example.walletkeep_demo.model.Category;
import com.example.walletkeep_demo.storage.FileStorageManager;
import com.example.walletkeep_demo.storage.PrefsManager;
import com.example.walletkeep_demo.ui.adapter.BillListAdapter;
import com.example.walletkeep_demo.util.DateAmountUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 记账小助手首页仪表盘：展示月度收支概览、预算监控进度条、快捷功能入口、
 * 最近 15 笔账单列表以及首次启动引导演示数据弹窗。
 */
public class MainActivity extends AppCompatActivity {

    private static final int RECENT_BILL_LIMIT = 15;
    private static final String STATE_MONTH = "state_home_month";

    private BillDao billDao;
    private PrefsManager prefsManager;

    private TextView btnPrevMonth;
    private TextView tvHomeMonth;
    private TextView btnNextMonth;
    private TextView tvHomeExpense;
    private TextView tvHomeIncome;
    private TextView tvHomeBalance;
    private TextView tvHomeBudgetLabel;
    private TextView tvHomeBudgetTotal;
    private ProgressBar pbHomeBudget;

    private View btnNavAdd;
    private View btnNavList;
    private View btnNavStats;
    private View btnNavSettings;
    private TextView tvViewAllBills;
    private ListView lvRecentBills;
    private TextView tvHomeEmpty;
    private TextView btnFabAdd;

    private BillListAdapter recentAdapter;
    private String currentMonth;
    private boolean appliedDarkTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }

        billDao = new BillDao(this);
        prefsManager = new PrefsManager(this);
        appliedDarkTheme = prefsManager.isDarkTheme();

        if (savedInstanceState != null) {
            currentMonth = savedInstanceState.getString(STATE_MONTH, DateAmountUtils.getCurrentMonth());
        } else {
            currentMonth = DateAmountUtils.getCurrentMonth();
        }
        if (currentMonth == null || currentMonth.trim().isEmpty()) {
            currentMonth = DateAmountUtils.getCurrentMonth();
        }

        bindViews();
        setupAdapterAndListeners();

        if (savedInstanceState == null && prefsManager.isFirstLaunch()) {
            prefsManager.setFirstLaunch(false);
            showOnboardingDialog();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefsManager.isDarkTheme() != appliedDarkTheme) {
            recreate();
            return;
        }
        loadDashboard();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_MONTH, currentMonth);
    }

    private void bindViews() {
        btnPrevMonth = findViewById(R.id.btn_home_prev_month);
        tvHomeMonth = findViewById(R.id.tv_home_month);
        btnNextMonth = findViewById(R.id.btn_home_next_month);
        tvHomeExpense = findViewById(R.id.tv_home_expense);
        tvHomeIncome = findViewById(R.id.tv_home_income);
        tvHomeBalance = findViewById(R.id.tv_home_balance);
        tvHomeBudgetLabel = findViewById(R.id.tv_home_budget_label);
        tvHomeBudgetTotal = findViewById(R.id.tv_home_budget_total);
        pbHomeBudget = findViewById(R.id.pb_home_budget);

        btnNavAdd = findViewById(R.id.btn_nav_add);
        btnNavList = findViewById(R.id.btn_nav_list);
        btnNavStats = findViewById(R.id.btn_nav_stats);
        btnNavSettings = findViewById(R.id.btn_nav_settings);
        tvViewAllBills = findViewById(R.id.tv_view_all_bills);
        lvRecentBills = findViewById(R.id.lv_recent_bills);
        tvHomeEmpty = findViewById(R.id.tv_home_empty);
        btnFabAdd = findViewById(R.id.btn_fab_add);

        lvRecentBills.setFocusable(false);
        ViewCompat.setElevation(btnFabAdd, dpToPx(6));
    }

    private void setupAdapterAndListeners() {
        recentAdapter = new BillListAdapter(
                this,
                new ArrayList<Bill>(),
                prefsManager.getDefaultCurrency()
        );
        lvRecentBills.setAdapter(recentAdapter);

        btnPrevMonth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentMonth = DateAmountUtils.shiftMonth(currentMonth, -1);
                loadDashboard();
            }
        });

        btnNextMonth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentMonth = DateAmountUtils.shiftMonth(currentMonth, 1);
                loadDashboard();
            }
        });

        View.OnClickListener openAddListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AddEditBillActivity.class));
            }
        };
        btnNavAdd.setOnClickListener(openAddListener);
        btnFabAdd.setOnClickListener(openAddListener);

        View.OnClickListener openListListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, BillListActivity.class));
            }
        };
        btnNavList.setOnClickListener(openListListener);
        tvViewAllBills.setOnClickListener(openListListener);

        btnNavStats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, StatsActivity.class));
            }
        });

        btnNavSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        lvRecentBills.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Bill bill = recentAdapter.getItem(position);
                if (bill != null) {
                    Intent intent = new Intent(MainActivity.this, AddEditBillActivity.class);
                    intent.putExtra(AddEditBillActivity.EXTRA_BILL_ID, bill.getId());
                    startActivity(intent);
                }
            }
        });

        lvRecentBills.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Bill bill = recentAdapter.getItem(position);
                if (bill != null) {
                    confirmDeleteBill(bill);
                }
                return true;
            }
        });
    }

    private void loadDashboard() {
        String currency = prefsManager.getDefaultCurrency();
        float monthlyBudget = prefsManager.getMonthlyBudget();
        boolean reminderEnabled = prefsManager.isReminderEnabled();

        tvHomeMonth.setText(formatMonthLabel(currentMonth) + " 收支总览");

        double monthExpense = billDao.getMonthlyTotal(currentMonth, Category.TYPE_EXPENSE);
        double monthIncome = billDao.getMonthlyTotal(currentMonth, Category.TYPE_INCOME);
        double balance = monthIncome - monthExpense;
        if (Math.abs(balance) < 0.005) {
            balance = 0.0;
        }

        tvHomeExpense.setText(DateAmountUtils.formatAmount(currency, monthExpense));
        tvHomeIncome.setText(DateAmountUtils.formatAmount(currency, monthIncome));
        tvHomeBalance.setText(formatBalanceAmount(currency, balance));

        updateBudgetMonitor(currency, monthlyBudget, monthExpense, reminderEnabled);

        List<Bill> recentBills = billDao.getRecentBills(RECENT_BILL_LIMIT);
        recentAdapter.updateData(recentBills, currency);

        if (recentBills.isEmpty()) {
            lvRecentBills.setVisibility(View.GONE);
            tvHomeEmpty.setVisibility(View.VISIBLE);
        } else {
            tvHomeEmpty.setVisibility(View.GONE);
            lvRecentBills.setVisibility(View.VISIBLE);
            setListViewHeightBasedOnChildren(lvRecentBills);
        }
    }

    private void updateBudgetMonitor(String currency, float monthlyBudget, double monthExpense, boolean reminderEnabled) {
        DateAmountUtils.BudgetStatus status = DateAmountUtils.evaluateBudget(monthlyBudget, monthExpense);
        if (status.level == DateAmountUtils.BudgetStatus.LEVEL_UNSET) {
            tvHomeBudgetLabel.setText("未设置月度预算，可前往设置开启");
            tvHomeBudgetTotal.setVisibility(View.GONE);
            pbHomeBudget.setProgress(0);
            applyBudgetProgressColors(ContextCompat.getColor(this, R.color.colorSafe));
            return;
        }

        tvHomeBudgetTotal.setVisibility(View.VISIBLE);
        tvHomeBudgetTotal.setText("预算 " + DateAmountUtils.formatCents(currency, status.budgetCents));
        pbHomeBudget.setProgress(status.progressPercent);

        int progressColor;
        if (status.level == DateAmountUtils.BudgetStatus.LEVEL_OVER && reminderEnabled) {
            tvHomeBudgetLabel.setText("本月已超支：" + DateAmountUtils.formatCents(currency, status.overCents) + "！");
            progressColor = ContextCompat.getColor(this, R.color.colorDanger);
        } else if (status.level == DateAmountUtils.BudgetStatus.LEVEL_WARNING && reminderEnabled) {
            tvHomeBudgetLabel.setText("预算即将用尽（已用 " + String.format(Locale.CHINA, "%.0f%%", status.usagePercent) + "）");
            progressColor = ContextCompat.getColor(this, R.color.colorWarning);
        } else {
            tvHomeBudgetLabel.setText("剩余预算：" + DateAmountUtils.formatCents(currency, status.remainingCents));
            progressColor = ContextCompat.getColor(this, R.color.colorSafe);
        }
        applyBudgetProgressColors(progressColor);
    }

    private void applyBudgetProgressColors(int progressColor) {
        Drawable drawable = pbHomeBudget.getProgressDrawable();
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
        pbHomeBudget.setProgressDrawable(mutated);
    }

    private void showOnboardingDialog() {
        new AlertDialog.Builder(this)
                .setTitle("欢迎使用记账小助手")
                .setMessage("是否一键生成 10 笔演示账单数据，方便体验月度统计图表、筛选查询和 CSV 导出功能？")
                .setPositiveButton("生成演示数据", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        billDao.seedDemoBills();
                        Toast.makeText(MainActivity.this, "已生成 10 笔演示账单", Toast.LENGTH_SHORT).show();
                        loadDashboard();
                    }
                })
                .setNegativeButton("暂不，空白开始", null)
                .show();
    }

    private void confirmDeleteBill(final Bill bill) {
        new AlertDialog.Builder(this)
                .setTitle("删除账单")
                .setMessage("确定删除这笔账单吗？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int deletedRows = billDao.delete(bill.getId());
                        if (deletedRows > 0) {
                            String receiptPath = bill.getReceiptPath() == null ? "" : bill.getReceiptPath().trim();
                            if (!receiptPath.isEmpty()) {
                                FileStorageManager.deleteReceiptFile(receiptPath);
                            }
                            Toast.makeText(MainActivity.this, "账单已删除", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "账单不存在或已被删除", Toast.LENGTH_SHORT).show();
                        }
                        loadDashboard();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
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

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
