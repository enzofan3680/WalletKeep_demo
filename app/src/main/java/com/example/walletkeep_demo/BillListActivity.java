package com.example.walletkeep_demo;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
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
import java.util.Locale;

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
        typeLabels.add("全部类型");
        typeLabels.add("仅支出");
        typeLabels.add("仅收入");
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
                    reloadCategorySpinner();
                    loadFilteredBills();
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

        reloadMonthSpinner();
        syncTypeSpinnerSelection();
        reloadCategorySpinner();
        loadFilteredBills();
    }

    private void reloadMonthSpinner() {
        List<String> distinctMonths = billDao.getDistinctMonths();
        String currentMonth = DateAmountUtils.getCurrentMonth();
        if (!distinctMonths.contains(currentMonth)) {
            distinctMonths.add(currentMonth);
            Collections.sort(distinctMonths, Collections.<String>reverseOrder());
        }

        monthValues.clear();
        monthLabels.clear();
        monthValues.add("");
        monthLabels.add("全部月份");

        for (String month : distinctMonths) {
            if (month != null && !month.trim().isEmpty()) {
                String normalized = month.trim();
                monthValues.add(normalized);
                monthLabels.add(normalized);
            }
        }
        monthSpinnerAdapter.notifyDataSetChanged();

        int targetIndex = monthValues.indexOf(selectedMonth);
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

    private void reloadCategorySpinner() {
        List<Category> categories = (selectedType == Category.TYPE_EXPENSE || selectedType == Category.TYPE_INCOME)
                ? categoryDao.getByType(selectedType)
                : categoryDao.getAll();

        categoryIds.clear();
        categoryLabels.clear();
        categoryIds.add(CATEGORY_ALL);
        categoryLabels.add("全部分类");

        for (Category category : categories) {
            if (category != null) {
                categoryIds.add(category.getId());
                categoryLabels.add(category.getName());
            }
        }
        categorySpinnerAdapter.notifyDataSetChanged();

        int targetIndex = -1;
        for (int i = 0; i < categoryIds.size(); i++) {
            if (categoryIds.get(i) == selectedCategoryId) {
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
        String currency = prefsManager.getDefaultCurrency();
        List<Bill> bills = billDao.queryBills(selectedMonth, selectedType, selectedCategoryId, searchKeyword);
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

        tvFilterCount.setText(String.format(Locale.CHINA, "共 %d 笔", bills.size()));
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
                .setTitle("删除账单")
                .setMessage("确定删除这笔账单吗？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int deletedRows = billDao.delete(bill.getId());
                        if (deletedRows > 0) {
                            String receiptPath = safeString(bill.getReceiptPath()).trim();
                            if (!receiptPath.isEmpty()) {
                                FileStorageManager.deleteReceiptFile(receiptPath);
                            }
                            Toast.makeText(BillListActivity.this, "账单已删除", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(BillListActivity.this, "账单不存在或已被删除", Toast.LENGTH_SHORT).show();
                        }
                        reloadMonthSpinner();
                        loadFilteredBills();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }
}
