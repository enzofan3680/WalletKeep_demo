package com.example.walletkeep_demo;

import android.content.Context;
import android.content.DialogInterface;
import android.database.SQLException;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.example.walletkeep_demo.db.BillDao;
import com.example.walletkeep_demo.db.CategoryDao;
import com.example.walletkeep_demo.model.Bill;
import com.example.walletkeep_demo.storage.FileStorageManager;
import com.example.walletkeep_demo.storage.PrefsManager;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 预算与备份设置页面：管理月度预算、预警开关、默认币种、深色主题（SharedPreferences），
 * 以及私有目录 CSV 备份导出、恢复、内容预览与演示/清空数据操作。
 */
public class SettingsActivity extends AppCompatActivity {

    private PrefsManager prefsManager;
    private BillDao billDao;
    private CategoryDao categoryDao;

    private EditText etMonthlyBudget;
    private TextView btnSaveBudget;
    private SwitchCompat switchBudgetReminder;
    private RadioGroup rgCurrency;
    private RadioButton rbCurrencyCny;
    private RadioButton rbCurrencyUsd;
    private RadioButton rbCurrencyEur;
    private SwitchCompat switchDarkTheme;

    private TextView tvBackupInfo;
    private TextView btnExportCsv;
    private TextView btnImportCsv;
    private TextView btnPreviewCsv;
    private TextView btnSeedDemo;
    private TextView btnClearAll;

    // 防止代码回填控件状态或 recreate 状态恢复时触发监听器循环
    private boolean isPopulatingUi = false;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private boolean isIoBusy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_settings);
        }

        prefsManager = new PrefsManager(this);
        billDao = new BillDao(this);
        categoryDao = new CategoryDao(this);

        bindViews();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        populateSettings();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
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
        etMonthlyBudget = findViewById(R.id.et_monthly_budget);
        btnSaveBudget = findViewById(R.id.btn_save_budget);
        switchBudgetReminder = findViewById(R.id.switch_budget_reminder);
        rgCurrency = findViewById(R.id.rg_currency);
        rbCurrencyCny = findViewById(R.id.rb_currency_cny);
        rbCurrencyUsd = findViewById(R.id.rb_currency_usd);
        rbCurrencyEur = findViewById(R.id.rb_currency_eur);
        switchDarkTheme = findViewById(R.id.switch_dark_theme);

        tvBackupInfo = findViewById(R.id.tv_backup_info);
        btnExportCsv = findViewById(R.id.btn_export_csv);
        btnImportCsv = findViewById(R.id.btn_import_csv);
        btnPreviewCsv = findViewById(R.id.btn_preview_csv);
        btnSeedDemo = findViewById(R.id.btn_seed_demo);
        btnClearAll = findViewById(R.id.btn_clear_all);
    }

    private void setupListeners() {
        btnSaveBudget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveBudget();
            }
        });

        switchBudgetReminder.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isPopulatingUi || isChecked == prefsManager.isReminderEnabled()) {
                    return;
                }
                prefsManager.setReminderEnabled(isChecked);
                Toast.makeText(
                        SettingsActivity.this,
                        isChecked ? "已开启预算超支预警" : "已关闭预算超支预警",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        rgCurrency.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (isPopulatingUi) {
                    return;
                }
                String chosenCurrency = currencyFromRadioId(checkedId);
                if (chosenCurrency.equals(prefsManager.getDefaultCurrency())) {
                    return;
                }
                prefsManager.setDefaultCurrency(chosenCurrency);
                Toast.makeText(
                        SettingsActivity.this,
                        "默认币种已切换为 " + chosenCurrency,
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        switchDarkTheme.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isPopulatingUi || isChecked == prefsManager.isDarkTheme()) {
                    return;
                }
                prefsManager.setThemeMode(isChecked ? PrefsManager.THEME_DARK : PrefsManager.THEME_LIGHT);
                prefsManager.applySavedTheme();
                recreate();
            }
        });

        btnExportCsv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleExportCsv();
            }
        });

        btnImportCsv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleImportCsv();
            }
        });

        btnPreviewCsv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handlePreviewCsv();
            }
        });

        btnSeedDemo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                billDao.seedDemoBills();
                Toast.makeText(SettingsActivity.this, "已生成 10 笔演示账单", Toast.LENGTH_SHORT).show();
            }
        });

        btnClearAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClearAllData();
            }
        });
    }

    private void populateSettings() {
        isPopulatingUi = true;
        try {
            long budgetCents = prefsManager.getMonthlyBudgetCents();
            etMonthlyBudget.setText(formatPlainCents(budgetCents));
            etMonthlyBudget.setSelection(etMonthlyBudget.getText().length());

            switchBudgetReminder.setChecked(prefsManager.isReminderEnabled());

            String currency = prefsManager.getDefaultCurrency();
            if ("$".equals(currency)) {
                rbCurrencyUsd.setChecked(true);
            } else if ("€".equals(currency)) {
                rbCurrencyEur.setChecked(true);
            } else {
                rbCurrencyCny.setChecked(true);
            }

            switchDarkTheme.setChecked(prefsManager.isDarkTheme());
            updateBackupInfo();
        } finally {
            isPopulatingUi = false;
        }
    }

    private void updateBackupInfo() {
        File exportsDir = FileStorageManager.getExportsDir(this);
        List<File> files = FileStorageManager.listBackupFiles(this);
        tvBackupInfo.setText(String.format(
                Locale.CHINA,
                "私有目录已保存 %d 个 CSV 备份文件\n备份目录：%s",
                files.size(),
                exportsDir.getAbsolutePath()
        ));
    }

    private void saveBudget() {
        String raw = etMonthlyBudget.getText() == null ? "" : etMonthlyBudget.getText().toString().trim();
        if (raw.isEmpty()) {
            Toast.makeText(this, "请输入有效的非负预算金额", Toast.LENGTH_SHORT).show();
            etMonthlyBudget.requestFocus();
            return;
        }
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效的非负预算金额", Toast.LENGTH_SHORT).show();
            etMonthlyBudget.requestFocus();
            return;
        }
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0) {
            Toast.makeText(this, "预算金额不能为负数", Toast.LENGTH_SHORT).show();
            etMonthlyBudget.requestFocus();
            return;
        }
        if (value > 999999999.99) {
            Toast.makeText(this, "预算金额超出允许范围", Toast.LENGTH_SHORT).show();
            etMonthlyBudget.requestFocus();
            return;
        }
        long budgetCents = Math.round(value * 100.0);
        prefsManager.setMonthlyBudgetCents(budgetCents);
        etMonthlyBudget.setText(formatPlainCents(budgetCents));
        etMonthlyBudget.setSelection(etMonthlyBudget.getText().length());
        hideKeyboard(etMonthlyBudget);
        Toast.makeText(this, "月度预算已更新", Toast.LENGTH_SHORT).show();
    }

    private static String formatPlainCents(long cents) {
        long safe = Math.max(0L, cents);
        return String.format(Locale.US, "%d.%02d", safe / 100L, safe % 100L);
    }

    private void handleExportCsv() {
        if (isIoBusy) {
            return;
        }
        setIoBusy(true);
        final Context appContext = getApplicationContext();
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Bill> bills = billDao.getAllBills();
                    if (bills.isEmpty()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (isUiInvalid()) {
                                    return;
                                }
                                setIoBusy(false);
                                Toast.makeText(SettingsActivity.this, "当前没有账单可导出", Toast.LENGTH_SHORT).show();
                            }
                        });
                        return;
                    }
                    final FileStorageManager.ExportResult result = FileStorageManager.exportBillsBackup(appContext, bills);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isUiInvalid()) {
                                return;
                            }
                            setIoBusy(false);
                            updateBackupInfo();
                            String message = String.format(
                                    Locale.CHINA,
                                    "共导出 %d 笔账单记录，同步备份 %d 张票据图片\n\n文件大小：%d 字节\n私有目录路径：\n%s",
                                    bills.size(),
                                    result.backedUpReceipts,
                                    result.csvFile.length(),
                                    result.csvFile.getAbsolutePath()
                            );
                            new AlertDialog.Builder(SettingsActivity.this)
                                    .setTitle("导出成功")
                                    .setMessage(message)
                                    .setPositiveButton("知道了", null)
                                    .show();
                        }
                    });
                } catch (final IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isUiInvalid()) {
                                return;
                            }
                            setIoBusy(false);
                            Toast.makeText(SettingsActivity.this, "导出失败：" + safeMessage(e), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private void handleImportCsv() {
        if (isIoBusy) {
            return;
        }
        final List<File> files = FileStorageManager.listBackupFiles(this);
        if (files.isEmpty()) {
            Toast.makeText(this, "暂无可恢复的 CSV 备份文件，请先导出", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = buildFileChoiceLabels(files);
        new AlertDialog.Builder(this)
                .setTitle("选择要恢复的 CSV 备份")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showImportModeDialog(files.get(which));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showImportModeDialog(final File chosenFile) {
        String[] modeLabels = new String[]{
                "覆盖恢复（推荐）：清空当前账单并按备份还原",
                "增量去重追加：保留当前账单，自动跳过重复项"
        };
        new AlertDialog.Builder(this)
                .setTitle("选择恢复方式")
                .setItems(modeLabels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int importMode = (which == 0)
                                ? BillDao.IMPORT_MODE_REPLACE
                                : BillDao.IMPORT_MODE_DEDUP_APPEND;
                        runImportCsvAsync(chosenFile, importMode);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void runImportCsvAsync(final File chosenFile, final int importMode) {
        if (isIoBusy) {
            return;
        }
        setIoBusy(true);
        final Context appContext = getApplicationContext();
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final FileStorageManager.RestoreResult result = FileStorageManager.importBillsFromCsv(
                            appContext,
                            chosenFile,
                            billDao,
                            categoryDao,
                            importMode
                    );
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isUiInvalid()) {
                                return;
                            }
                            setIoBusy(false);
                            StringBuilder msg = new StringBuilder();
                            msg.append("恢复完成：导入 ").append(result.importedCount).append(" 笔账单");
                            if (result.skippedCount > 0) {
                                msg.append("，跳过重复 ").append(result.skippedCount).append(" 笔");
                            }
                            if (result.updatedReceiptCount > 0) {
                                msg.append("，补齐票据关联 ").append(result.updatedReceiptCount).append(" 笔");
                            }
                            if (result.restoredReceipts > 0) {
                                msg.append("，还原票据 ").append(result.restoredReceipts).append(" 张");
                            }
                            if (result.missingReceipts > 0) {
                                msg.append("（").append(result.missingReceipts).append(" 张票据快照缺失已自动忽略）");
                            }
                            Toast.makeText(SettingsActivity.this, msg.toString(), Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (final IOException e) {
                    postImportError("读取备份失败：" + safeMessage(e));
                } catch (final IllegalArgumentException e) {
                    postImportError("CSV 格式错误：" + safeMessage(e));
                } catch (final IllegalStateException e) {
                    postImportError("导入失败：" + safeMessage(e));
                } catch (final SQLException e) {
                    postImportError("数据库写入失败：" + safeMessage(e));
                }
            }
        });
    }

    private void postImportError(final String errorMessage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isUiInvalid()) {
                    return;
                }
                setIoBusy(false);
                Toast.makeText(SettingsActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handlePreviewCsv() {
        if (isIoBusy) {
            return;
        }
        final List<File> files = FileStorageManager.listBackupFiles(this);
        if (files.isEmpty()) {
            Toast.makeText(this, "暂无可预览的 CSV 备份文件，请先导出", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = buildFileChoiceLabels(files);
        new AlertDialog.Builder(this)
                .setTitle("选择要预览的 CSV 文件")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final File chosenFile = files.get(which);
                        setIoBusy(true);
                        ioExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final String content = FileStorageManager.readFileText(chosenFile);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (isUiInvalid()) {
                                                return;
                                            }
                                            setIoBusy(false);
                                            showCsvPreviewDialog(chosenFile, content);
                                        }
                                    });
                                } catch (final IOException e) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (isUiInvalid()) {
                                                return;
                                            }
                                            setIoBusy(false);
                                            Toast.makeText(SettingsActivity.this, "读取文件失败：" + safeMessage(e), Toast.LENGTH_LONG).show();
                                        }
                                    });
                                }
                            }
                        });
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void setIoBusy(boolean busy) {
        isIoBusy = busy;
        btnExportCsv.setEnabled(!busy);
        btnImportCsv.setEnabled(!busy);
        btnPreviewCsv.setEnabled(!busy);
        btnSeedDemo.setEnabled(!busy);
        btnClearAll.setEnabled(!busy);
    }

    private boolean isUiInvalid() {
        if (isFinishing()) {
            return true;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed();
    }

    private static String safeMessage(Throwable t) {
        String msg = t == null ? null : t.getMessage();
        return (msg == null || msg.trim().isEmpty()) ? "操作异常" : msg;
    }

    private void showCsvPreviewDialog(File file, String content) {
        int padding = dpToPx(16);
        ScrollView verticalScroll = new ScrollView(this);
        HorizontalScrollView horizontalScroll = new HorizontalScrollView(this);
        TextView tvContent = new TextView(this);
        tvContent.setPadding(padding, padding, padding, padding);
        tvContent.setTypeface(Typeface.MONOSPACE);
        tvContent.setTextSize(12f);
        tvContent.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
        tvContent.setTextIsSelectable(true);
        tvContent.setText(content == null || content.isEmpty() ? "(空文件)" : content);

        horizontalScroll.addView(tvContent);
        verticalScroll.addView(horizontalScroll);

        new AlertDialog.Builder(this)
                .setTitle(file.getName() + " (" + formatFileSize(file.length()) + ")")
                .setView(verticalScroll)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void confirmClearAllData() {
        new AlertDialog.Builder(this)
                .setTitle("确认清空数据")
                .setMessage("确定清空所有账单记录及票据图片吗？该操作不可撤销（CSV 备份文件会保留）。")
                .setPositiveButton("确定清空", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        billDao.deleteAllBills();
                        FileStorageManager.clearAllReceipts(SettingsActivity.this);
                        Toast.makeText(SettingsActivity.this, "已清空全部账单与票据文件", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String[] buildFileChoiceLabels(List<File> files) {
        String[] labels = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            labels[i] = file.getName() + " (" + formatFileSize(file.length()) + ")";
        }
        return labels;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        return String.format(Locale.CHINA, "%.1f KB", bytes / 1024.0);
    }

    private String currencyFromRadioId(int checkedId) {
        if (checkedId == R.id.rb_currency_usd) {
            return "$";
        }
        if (checkedId == R.id.rb_currency_eur) {
            return "€";
        }
        return "¥";
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
