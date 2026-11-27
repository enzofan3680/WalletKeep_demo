package com.example.walletkeep_demo;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
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
import com.example.walletkeep_demo.util.UiUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private int loadToken;

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdown();
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
        ViewCompat.setElevation(btnFabAdd, UiUtils.dpToPx(this, 6));
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
        final String month = currentMonth;
        final int token = ++loadToken;
        final String currency = prefsManager.getDefaultCurrency();
        final long monthlyBudgetCents = prefsManager.getMonthlyBudgetCents();
        final boolean reminderEnabled = prefsManager.isReminderEnabled();

        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final double monthExpense = billDao.getMonthlyTotal(month, Category.TYPE_EXPENSE);
                final double monthIncome = billDao.getMonthlyTotal(month, Category.TYPE_INCOME);
                final List<Bill> recentBills = billDao.getRecentBills(RECENT_BILL_LIMIT);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (token != loadToken || isUiInvalid()) {
                            return;
                        }
                        applyDashboard(month, currency, monthlyBudgetCents, reminderEnabled,
                                monthExpense, monthIncome, recentBills);
                    }
                });
            }
        });
    }

    private void applyDashboard(String month, String currency, long monthlyBudgetCents,
                                boolean reminderEnabled, double monthExpense, double monthIncome,
                                List<Bill> recentBills) {
        tvHomeMonth.setText(getString(R.string.fmt_home_month_overview, UiUtils.formatMonthLabel(month)));

        double balance = monthIncome - monthExpense;
        if (Math.abs(balance) < 0.005) {
            balance = 0.0;
        }

        tvHomeExpense.setText(DateAmountUtils.formatAmount(currency, monthExpense));
        tvHomeIncome.setText(DateAmountUtils.formatAmount(currency, monthIncome));
        tvHomeBalance.setText(UiUtils.formatBalanceAmount(currency, balance));

        updateBudgetMonitor(currency, monthlyBudgetCents, monthExpense, reminderEnabled);

        recentAdapter.updateData(recentBills, currency);

        if (recentBills.isEmpty()) {
            lvRecentBills.setVisibility(View.GONE);
            tvHomeEmpty.setVisibility(View.VISIBLE);
        } else {
            tvHomeEmpty.setVisibility(View.GONE);
            lvRecentBills.setVisibility(View.VISIBLE);
            UiUtils.setListViewHeightBasedOnChildren(lvRecentBills);
        }
    }

    private void updateBudgetMonitor(String currency, long monthlyBudgetCents, double monthExpense, boolean reminderEnabled) {
        DateAmountUtils.BudgetStatus status = DateAmountUtils.evaluateBudgetCents(monthlyBudgetCents, monthExpense);
        if (status.level == DateAmountUtils.BudgetStatus.LEVEL_UNSET) {
            tvHomeBudgetLabel.setText(R.string.budget_unset_hint);
            tvHomeBudgetTotal.setVisibility(View.GONE);
            pbHomeBudget.setProgress(0);
            UiUtils.applyBudgetProgressColors(pbHomeBudget, ContextCompat.getColor(this, R.color.colorSafe));
            return;
        }

        tvHomeBudgetTotal.setVisibility(View.VISIBLE);
        tvHomeBudgetTotal.setText(getString(R.string.fmt_budget_total, DateAmountUtils.formatCents(currency, status.budgetCents)));
        pbHomeBudget.setProgress(status.progressPercent);

        int progressColor;
        if (status.level == DateAmountUtils.BudgetStatus.LEVEL_OVER && reminderEnabled) {
            tvHomeBudgetLabel.setText(getString(R.string.fmt_budget_over, DateAmountUtils.formatCents(currency, status.overCents)));
            progressColor = ContextCompat.getColor(this, R.color.colorDanger);
        } else if (status.level == DateAmountUtils.BudgetStatus.LEVEL_WARNING && reminderEnabled) {
            tvHomeBudgetLabel.setText(getString(R.string.fmt_budget_warning, status.usagePercent));
            progressColor = ContextCompat.getColor(this, R.color.colorWarning);
        } else {
            tvHomeBudgetLabel.setText(getString(R.string.fmt_budget_remaining, DateAmountUtils.formatCents(currency, status.remainingCents)));
            progressColor = ContextCompat.getColor(this, R.color.colorSafe);
        }
        UiUtils.applyBudgetProgressColors(pbHomeBudget, progressColor);
    }

    private void showOnboardingDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.onboarding_title)
                .setMessage(R.string.onboarding_message)
                .setPositiveButton(R.string.onboarding_generate, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 用户作出选择后才写入首次启动标记；若进程在弹窗期间被杀，下次仍会提示
                        prefsManager.setFirstLaunch(false);
                        seedDemoDataAndRefresh();
                    }
                })
                .setNegativeButton(R.string.onboarding_skip, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        prefsManager.setFirstLaunch(false);
                    }
                })
                .show();
    }

    private void seedDemoDataAndRefresh() {
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                billDao.seedDemoBills();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isUiInvalid()) {
                            return;
                        }
                        Toast.makeText(MainActivity.this, R.string.toast_demo_seeded, Toast.LENGTH_SHORT).show();
                        loadDashboard();
                    }
                });
            }
        });
    }

    private void confirmDeleteBill(final Bill bill) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_bill_title)
                .setMessage(R.string.dialog_delete_bill_message)
                .setPositiveButton(R.string.action_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final long billId = bill.getId();
                        final String receiptPath = bill.getReceiptPath() == null
                                ? "" : bill.getReceiptPath().trim();
                        dbExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                final int deletedRows = billDao.delete(billId);
                                if (deletedRows > 0 && !receiptPath.isEmpty()
                                        && billDao.countBillsByReceiptPath(receiptPath) == 0) {
                                    FileStorageManager.deleteReceiptFile(receiptPath);
                                }
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (isUiInvalid()) {
                                            return;
                                        }
                                        if (deletedRows > 0) {
                                            Toast.makeText(MainActivity.this, R.string.toast_bill_deleted, Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(MainActivity.this, R.string.toast_bill_not_found, Toast.LENGTH_SHORT).show();
                                        }
                                        loadDashboard();
                                    }
                                });
                            }
                        });
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private boolean isUiInvalid() {
        return isFinishing() || isDestroyed();
    }
}
