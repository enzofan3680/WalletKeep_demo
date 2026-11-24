package com.example.walletkeep_demo;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
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
            float budget = prefsManager.getMonthlyBudget();
            etMonthlyBudget.setText(String.format(Locale.CHINA, "%.2f", budget));
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
        double rounded = Math.round(value * 100.0) / 100.0;
        prefsManager.setMonthlyBudget((float) rounded);
        etMonthlyBudget.setText(String.format(Locale.CHINA, "%.2f", rounded));
        etMonthlyBudget.setSelection(etMonthlyBudget.getText().length());
        hideKeyboard(etMonthlyBudget);
        Toast.makeText(this, "月度预算已更新", Toast.LENGTH_SHORT).show();
    }

    private void handleExportCsv() {
        List<Bill> bills = billDao.getAllBills();
        if (bills.isEmpty()) {
            Toast.makeText(this, "当前没有账单可导出", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File exportedFile = FileStorageManager.exportBillsToCsv(this, bills);
            updateBackupInfo();
            String message = String.format(
                    Locale.CHINA,
                    "共导出 %d 笔账单记录\n\n文件大小：%d 字节\n私有目录路径：\n%s",
                    bills.size(),
                    exportedFile.length(),
                    exportedFile.getAbsolutePath()
            );
            new AlertDialog.Builder(this)
                    .setTitle("导出成功")
                    .setMessage(message)
                    .setPositiveButton("知道了", null)
                    .show();
        } catch (IOException e) {
            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleImportCsv() {
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
                        File chosenFile = files.get(which);
                        try {
                            int importedCount = FileStorageManager.importBillsFromCsv(
                                    SettingsActivity.this,
                                    chosenFile,
                                    billDao,
                                    categoryDao
                            );
                            Toast.makeText(
                                    SettingsActivity.this,
                                    String.format(Locale.CHINA, "成功导入 %d 笔账单", importedCount),
                                    Toast.LENGTH_SHORT
                            ).show();
                        } catch (IOException e) {
                            Toast.makeText(SettingsActivity.this, "读取备份失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                        } catch (IllegalArgumentException e) {
                            Toast.makeText(SettingsActivity.this, "CSV 格式错误：" + e.getMessage(), Toast.LENGTH_LONG).show();
                        } catch (IllegalStateException e) {
                            Toast.makeText(SettingsActivity.this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void handlePreviewCsv() {
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
                        File chosenFile = files.get(which);
                        try {
                            String content = FileStorageManager.readFileText(chosenFile);
                            showCsvPreviewDialog(chosenFile, content);
                        } catch (IOException e) {
                            Toast.makeText(SettingsActivity.this, "读取文件失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
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
