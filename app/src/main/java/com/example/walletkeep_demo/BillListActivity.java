package com.example.walletkeep_demo;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.walletkeep_demo.db.BillDao;
import com.example.walletkeep_demo.db.CategoryDao;
import com.example.walletkeep_demo.model.Bill;
import com.example.walletkeep_demo.model.Category;
import com.example.walletkeep_demo.storage.FileStorageManager;
import com.example.walletkeep_demo.storage.PrefsManager;
import com.example.walletkeep_demo.ui.adapter.BillListAdapter;
import com.example.walletkeep_demo.util.DateAmountUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 账单明细查询与筛选页面：支持关键词实时搜索、月份/收支类型/分类三维联动筛选、
 * 汇总统计、点击编辑及长按确认删除（同步清理票据文件）。
 */
public class BillListActivity extends AppCompatActivity {

    private static final int TYPE_ALL = -1;
    private static final long CATEGORY_ALL = -1L;

    private static final String STATE_MONTH = "state_month";
    private static final String STATE_TYPE = "state_type";
    private static final String STATE_CATEGORY_ID = "state_category_id";
    private static final String STATE_KEYWORD = "state_keyword";

    private BillDao billDao;
    private CategoryDao categoryDao;
    private PrefsManager prefsManager;

    private EditText etSearchKeyword;
    private TextView btnClearSearch;
    private Spinner spFilterMonth;
    private Spinner spFilterType;
    private Spinner spFilterCategory;
    private TextView tvFilterCount;
    private TextView tvFilterExpense;
    private TextView tvFilterIncome;
    private ListView lvBills;
    private TextView tvEmptyBills;

    private BillListAdapter billAdapter;
    private ArrayAdapter<String> monthSpinnerAdapter;
    private ArrayAdapter<String> typeSpinnerAdapter;
    private ArrayAdapter<String> categorySpinnerAdapter;

    private final List<String> monthValues = new ArrayList<String>();
    private final List<String> monthLabels = new ArrayList<String>();
    private final int[] typeValues = new int[] {
            TYPE_ALL,
            Category.TYPE_EXPENSE,
            Category.TYPE_INCOME
    };
    private final List<Long> categoryIds = new ArrayList<Long>();
    private final List<String> categoryLabels = new ArrayList<String>();

    private String selectedMonth = "";
    private int selectedType = TYPE_ALL;
    private long selectedCategoryId = CATEGORY_ALL;
    private String searchKeyword = "";
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private int loadToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bill_list);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_bill_list);
        }

        billDao = new BillDao(this);
        categoryDao = new CategoryDao(this);
        prefsManager = new PrefsManager(this);

        if (savedInstanceState != null) {
            selectedMonth = safeString(savedInstanceState.getString(STATE_MONTH, ""));
            selectedType = savedInstanceState.getInt(STATE_TYPE, TYPE_ALL);
            selectedCategoryId = savedInstanceState.getLong(STATE_CATEGORY_ID, CATEGORY_ALL);
            searchKeyword = safeString(savedInstanceState.getString(STATE_KEYWORD, "")).trim();
        }

        bindViews();
        setupAdapters();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFilterOptionsAndBills();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_MONTH, selectedMonth);
        outState.putInt(STATE_TYPE, selectedType);
        outState.putLong(STATE_CATEGORY_ID, selectedCategoryId);
        outState.putString(STATE_KEYWORD, searchKeyword);
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
        etSearchKeyword = findViewById(R.id.et_search_keyword);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        spFilterMonth = findViewById(R.id.sp_filter_month);
        spFilterType = findViewById(R.id.sp_filter_type);
        spFilterCategory = findViewById(R.id.sp_filter_category);
        tvFilterCount = findViewById(R.id.tv_filter_count);
        tvFilterExpense = findViewById(R.id.tv_filter_expense);
        tvFilterIncome = findViewById(R.id.tv_filter_income);
        lvBills = findViewById(R.id.lv_bills);
        tvEmptyBills = findViewById(R.id.tv_empty_bills);
    }

    private void setupAdapters() {
        billAdapter = new BillListAdapter(this, new ArrayList<Bill>(), prefsManager.getDefaultCurrency());
        lvBills.setAdapter(billAdapter);

        monthSpinnerAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                monthLabels
        );
        monthSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFilterMonth.setAdapter(monthSpinnerAdapter);

        List<String> typeLabels = new ArrayList<String>(3);
        typeLabels.add(getString(R.string.filter_type_all));
        typeLabels.add(getString(R.string.filter_type_expense));
        typeLabels.add(getString(R.string.filter_type_income));
        typeSpinnerAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                typeLabels
        );
        typeSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFilterType.setAdapter(typeSpinnerAdapter);

        categorySpinnerAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                categoryLabels
        );
        categorySpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFilterCategory.setAdapter(categorySpinnerAdapter);
    }

    private void setupListeners() {
        etSearchKeyword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String raw = (s == null) ? "" : s.toString();
                btnClearSearch.setVisibility(raw.isEmpty() ? View.GONE : View.VISIBLE);
                String trimmed = raw.trim();
                if (!trimmed.equals(searchKeyword)) {
                    searchKeyword = trimmed;
                    loadFilteredBills();
                }
            }
        });

        etSearchKeyword.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    searchKeyword = safeString(etSearchKeyword.getText().toString()).trim();
                    loadFilteredBills();
                    hideKeyboard(etSearchKeyword);
                    return true;
                }
                return false;
            }
        });

        btnClearSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etSearchKeyword.setText("");
                etSearchKeyword.requestFocus();
            }
        });

        spFilterMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= monthValues.size()) {
                    return;
                }
                String pickedMonth = monthValues.get(position);
                if (!pickedMonth.equals(selectedMonth)) {
                    selectedMonth = pickedMonth;
                    loadFilteredBills();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spFilterType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= typeValues.length) {
                    return;
                }
                int pickedType = typeValues[position];
                if (pickedType != selectedType) {
                    selectedType = pickedType;
                    reloadCategoriesAndBills();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spFilterCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= categoryIds.size()) {
                    return;
                }
                long pickedCategoryId = categoryIds.get(position);
                if (pickedCategoryId != selectedCategoryId) {
                    selectedCategoryId = pickedCategoryId;
                    loadFilteredBills();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        lvBills.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Bill bill = billAdapter.getItem(position);
                if (bill != null) {
                    Intent intent = new Intent(BillListActivity.this, AddEditBillActivity.class);
                    intent.putExtra(AddEditBillActivity.EXTRA_BILL_ID, bill.getId());
                    startActivity(intent);
                }
            }
        });

        lvBills.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Bill bill = billAdapter.getItem(position);
                if (bill != null) {
                    confirmDeleteBill(bill);
                }
                return true;
            }
        });
    }

    private void refreshFilterOptionsAndBills() {
        String currentInput = safeString(etSearchKeyword.getText().toString());
        btnClearSearch.setVisibility(currentInput.isEmpty() ? View.GONE : View.VISIBLE);
        searchKeyword = currentInput.trim();

        final int token = ++loadToken;
        final String monthSnapshot = selectedMonth;
        final int typeSnapshot = selectedType;
        final long categorySnapshot = selectedCategoryId;
        final String keywordSnapshot = searchKeyword;
        final String allMonthsLabel = getString(R.string.filter_all_months);
        final String allCategoriesLabel = getString(R.string.filter_all_categories);

        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<String> optionMonths = buildMonthValues(billDao.getDistinctMonths());
                    final List<String> optionMonthLabels = buildMonthLabels(optionMonths, allMonthsLabel);
                    final List<Long> optionIds = new ArrayList<Long>();
                    final List<String> optionLabels = new ArrayList<String>();
                    optionIds.add(CATEGORY_ALL);
                    optionLabels.add(allCategoriesLabel);
                    for (Category category : loadCategoriesForType(typeSnapshot)) {
                        if (category != null) {
                            optionIds.add(category.getId());
                            optionLabels.add(category.getName());
                        }
                    }
                    final long resolvedCategory = containsCategoryId(optionIds, categorySnapshot)
                            ? categorySnapshot : CATEGORY_ALL;
                    final String resolvedMonth = optionMonths.contains(monthSnapshot) ? monthSnapshot : "";
                    final List<Bill> bills = billDao.queryBills(
                            resolvedMonth, typeSnapshot, resolvedCategory, keywordSnapshot);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (token != loadToken || isUiInvalid()) {
                                return;
                            }
                            applyMonthOptions(optionMonths, optionMonthLabels, resolvedMonth);
                            syncTypeSpinnerSelection();
                            applyCategoryOptions(optionIds, optionLabels, resolvedCategory);
                            renderBills(bills);
                        }
                    });
                } catch (final RuntimeException e) {
                    Log.e("BillListActivity", "refresh filters failed", e);
                    postError(R.string.error_load_failed);
                }
            }
        });
    }

    private static List<String> buildMonthValues(List<String> distinctMonths) {
        List<String> result = new ArrayList<String>();
        result.add("");
        List<String> months = new ArrayList<String>();
        if (distinctMonths != null) {
            for (String month : distinctMonths) {
                if (month != null && !month.trim().isEmpty()) {
                    months.add(month.trim());
                }
            }
        }
        String currentMonth = DateAmountUtils.getCurrentMonth();
        if (!months.contains(currentMonth)) {
            months.add(currentMonth);
        }
        Collections.sort(months, Collections.<String>reverseOrder());
        result.addAll(months);
        return result;
    }

    private static List<String> buildMonthLabels(List<String> monthValues, String allMonthsLabel) {
        List<String> labels = new ArrayList<String>(monthValues.size());
        for (String month : monthValues) {
            labels.add(month.isEmpty() ? allMonthsLabel : month);
        }
        return labels;
    }

    private List<Category> loadCategoriesForType(int type) {
        if (type == Category.TYPE_EXPENSE || type == Category.TYPE_INCOME) {
            return categoryDao.getByType(type);
        }
        return categoryDao.getAll();
    }

    private static boolean containsCategoryId(List<Long> ids, long categoryId) {
        for (long id : ids) {
            if (id == categoryId) {
                return true;
            }
        }
        return false;
    }

    private void applyMonthOptions(List<String> values, List<String> labels, String resolvedMonth) {
        monthValues.clear();
        monthValues.addAll(values);
        monthLabels.clear();
        monthLabels.addAll(labels);
        monthSpinnerAdapter.notifyDataSetChanged();

        selectedMonth = resolvedMonth;
        int targetIndex = monthValues.indexOf(resolvedMonth);
        if (targetIndex < 0) {
            selectedMonth = "";
            targetIndex = 0;
        }
        spFilterMonth.setSelection(targetIndex, false);
    }

    private void syncTypeSpinnerSelection() {
        int targetIndex = 0;
        for (int i = 0; i < typeValues.length; i++) {
            if (typeValues[i] == selectedType) {
                targetIndex = i;
                break;
            }
        }
        if (typeValues[targetIndex] != selectedType) {
            selectedType = TYPE_ALL;
        }
        spFilterType.setSelection(targetIndex, false);
    }

    private void applyCategoryOptions(List<Long> ids, List<String> labels, long resolvedCategory) {
        categoryIds.clear();
        categoryIds.addAll(ids);
        categoryLabels.clear();
        categoryLabels.addAll(labels);
        categorySpinnerAdapter.notifyDataSetChanged();

        selectedCategoryId = resolvedCategory;
        int targetIndex = -1;
        for (int i = 0; i < categoryIds.size(); i++) {
            if (categoryIds.get(i) == resolvedCategory) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex < 0) {
            selectedCategoryId = CATEGORY_ALL;
            targetIndex = 0;
        }
        spFilterCategory.setSelection(targetIndex, false);
    }

    private void loadFilteredBills() {
        final int token = ++loadToken;
        final String month = selectedMonth;
        final int type = selectedType;
        final long categoryId = selectedCategoryId;
        final String keyword = searchKeyword;

        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Bill> bills = billDao.queryBills(month, type, categoryId, keyword);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (token != loadToken || isUiInvalid()) {
                                return;
                            }
                            renderBills(bills);
                        }
                    });
                } catch (final RuntimeException e) {
                    Log.e("BillListActivity", "load filtered bills failed", e);
                    postError(R.string.error_load_failed);
                }
            }
        });
    }

    private void reloadCategoriesAndBills() {
        final int token = ++loadToken;
        final int type = selectedType;
        final long categorySnapshot = selectedCategoryId;
        final String month = selectedMonth;
        final String keyword = searchKeyword;
        final String allCategoriesLabel = getString(R.string.filter_all_categories);

        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Long> optionIds = new ArrayList<Long>();
                    final List<String> optionLabels = new ArrayList<String>();
                    optionIds.add(CATEGORY_ALL);
                    optionLabels.add(allCategoriesLabel);
                    for (Category category : loadCategoriesForType(type)) {
                        if (category != null) {
                            optionIds.add(category.getId());
                            optionLabels.add(category.getName());
                        }
                    }
                    final long resolvedCategory = containsCategoryId(optionIds, categorySnapshot)
                            ? categorySnapshot : CATEGORY_ALL;
                    final List<Bill> bills = billDao.queryBills(month, type, resolvedCategory, keyword);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (token != loadToken || isUiInvalid()) {
                                return;
                            }
                            applyCategoryOptions(optionIds, optionLabels, resolvedCategory);
                            renderBills(bills);
                        }
                    });
                } catch (final RuntimeException e) {
                    Log.e("BillListActivity", "reload categories failed", e);
                    postError(R.string.error_load_failed);
                }
            }
        });
    }

    private void renderBills(List<Bill> bills) {
        String currency = prefsManager.getDefaultCurrency();
        billAdapter.updateData(bills, currency);

        double totalExpense = 0.0;
        double totalIncome = 0.0;
        for (Bill bill : bills) {
            if (bill.getType() == Category.TYPE_INCOME) {
                totalIncome += bill.getAmount();
            } else {
                totalExpense += bill.getAmount();
            }
        }

        tvFilterCount.setText(getString(R.string.fmt_filter_count, bills.size()));
        tvFilterExpense.setText(getString(R.string.fmt_filter_expense, DateAmountUtils.formatAmount(currency, totalExpense)));
        tvFilterIncome.setText(getString(R.string.fmt_filter_income, DateAmountUtils.formatAmount(currency, totalIncome)));

        if (bills.isEmpty()) {
            lvBills.setVisibility(View.GONE);
            tvEmptyBills.setVisibility(View.VISIBLE);
        } else {
            tvEmptyBills.setVisibility(View.GONE);
            lvBills.setVisibility(View.VISIBLE);
        }
    }

    private void confirmDeleteBill(final Bill bill) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_bill_title)
                .setMessage(R.string.dialog_delete_bill_message)
                .setPositiveButton(R.string.action_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final long billId = bill.getId();
                        final String receiptPath = safeString(bill.getReceiptPath()).trim();
                        dbExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final int deletedRows = billDao.delete(billId);
                                    if (deletedRows > 0 && !receiptPath.isEmpty()
                                            && billDao.countBillsByReceiptPath(receiptPath) == 0) {
                                        FileStorageManager.deleteReceiptFile(getApplicationContext(), receiptPath);
                                    }
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (isUiInvalid()) {
                                                return;
                                            }
                                            if (deletedRows > 0) {
                                                Toast.makeText(BillListActivity.this, R.string.toast_bill_deleted, Toast.LENGTH_SHORT).show();
                                            } else {
                                                Toast.makeText(BillListActivity.this, R.string.toast_bill_not_found, Toast.LENGTH_SHORT).show();
                                            }
                                            refreshFilterOptionsAndBills();
                                        }
                                    });
                                } catch (final RuntimeException e) {
                                    Log.e("BillListActivity", "delete bill failed", e);
                                    postError(R.string.error_operation_generic);
                                }
                            }
                        });
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void postError(final int messageRes) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isUiInvalid()) {
                    Toast.makeText(BillListActivity.this, messageRes, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private boolean isUiInvalid() {
        return isFinishing() || isDestroyed();
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }
}
