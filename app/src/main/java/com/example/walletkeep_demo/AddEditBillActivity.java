package com.example.walletkeep_demo;

import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.walletkeep_demo.db.BillDao;
import com.example.walletkeep_demo.db.CategoryDao;
import com.example.walletkeep_demo.model.Bill;
import com.example.walletkeep_demo.model.Category;
import com.example.walletkeep_demo.storage.FileStorageManager;
import com.example.walletkeep_demo.storage.PrefsManager;
import com.example.walletkeep_demo.ui.adapter.CategoryGridAdapter;
import com.example.walletkeep_demo.util.DateAmountUtils;
import com.example.walletkeep_demo.util.UiUtils;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 记一笔 / 编辑账单页面：支持收支切换、金额输入、动态网格分类选择、
 * 自定义分类增删、日期选择、备注以及私有目录票据图片附件管理。
 */
public class AddEditBillActivity extends AppCompatActivity {

    public static final String EXTRA_BILL_ID = "extra_bill_id";

    private static final int CATEGORY_COLUMNS = 4;

    private static final String STATE_TYPE = "state_type";
    private static final String STATE_CATEGORY_ID = "state_category_id";
    private static final String STATE_DATE = "state_date";
    private static final String STATE_CURRENT_RECEIPT = "state_current_receipt";
    private static final String STATE_PENDING_RECEIPT = "state_pending_receipt";

    private BillDao billDao;
    private CategoryDao categoryDao;
    private PrefsManager prefsManager;
    private CategoryGridAdapter categoryAdapter;

    private TextView tvTabExpense;
    private TextView tvTabIncome;
    private TextView tvCurrencySymbol;
    private EditText etAmount;
    private TextView btnAddCategory;
    private GridView gvCategories;
    private View layoutDatePicker;
    private TextView tvBillDate;
    private EditText etNote;
    private TextView btnPickReceipt;
    private View layoutReceiptPreview;
    private ImageView ivReceiptThumb;
    private TextView tvReceiptPath;
    private TextView btnRemoveReceipt;
    private Button btnSaveBill;
    private Button btnDeleteBill;

    private long editBillId = -1L;
    private boolean isEditMode = false;
    private Bill existingBill;

    private int currentType = Category.TYPE_EXPENSE;
    private String selectedDate = "";
    private String originalReceiptPath = "";
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private boolean isCopyingReceipt = false;
    private String currentReceiptPath = "";
    private String pendingNewReceiptPath = "";
    private boolean savedSuccessfully = false;
    private ActivityResultLauncher<String> receiptPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_bill);

        billDao = new BillDao(this);
        categoryDao = new CategoryDao(this);
        prefsManager = new PrefsManager(this);

        editBillId = getIntent().getLongExtra(EXTRA_BILL_ID, -1L);
        isEditMode = editBillId > 0L;

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(isEditMode ? R.string.title_edit_bill : R.string.title_add_bill);
        }

        bindViews();

        if (isEditMode) {
            existingBill = billDao.getById(editBillId);
            if (existingBill == null) {
                Toast.makeText(this, R.string.toast_bill_not_found, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            originalReceiptPath = safeTrim(existingBill.getReceiptPath());
        } else {
            originalReceiptPath = "";
        }

        long initialCategoryId = -1L;
        if (savedInstanceState != null) {
            currentType = savedInstanceState.getInt(STATE_TYPE, Category.TYPE_EXPENSE);
            initialCategoryId = savedInstanceState.getLong(STATE_CATEGORY_ID, -1L);
            selectedDate = savedInstanceState.getString(STATE_DATE, DateAmountUtils.getCurrentDate());
            currentReceiptPath = savedInstanceState.getString(STATE_CURRENT_RECEIPT, "");
            pendingNewReceiptPath = savedInstanceState.getString(STATE_PENDING_RECEIPT, "");
        } else if (isEditMode) {
            currentType = (existingBill.getType() == Category.TYPE_INCOME)
                    ? Category.TYPE_INCOME
                    : Category.TYPE_EXPENSE;
            initialCategoryId = existingBill.getCategoryId();
            String savedDate = safeTrim(existingBill.getBillDate());
            selectedDate = savedDate.isEmpty() ? DateAmountUtils.getCurrentDate() : savedDate;
            currentReceiptPath = originalReceiptPath;
            pendingNewReceiptPath = "";

            etAmount.setText(String.format(Locale.US, "%.2f", existingBill.getAmount()));
            etNote.setText(existingBill.getNote() != null ? existingBill.getNote() : "");
        } else {
            currentType = Category.TYPE_EXPENSE;
            selectedDate = DateAmountUtils.getCurrentDate();
            currentReceiptPath = "";
            pendingNewReceiptPath = "";
        }

        tvCurrencySymbol.setText(prefsManager.getDefaultCurrency());
        btnSaveBill.setText(isEditMode ? R.string.action_save_changes : R.string.action_save_bill);
        btnDeleteBill.setVisibility(isEditMode ? View.VISIBLE : View.GONE);

        categoryAdapter = new CategoryGridAdapter(this, null);
        gvCategories.setAdapter(categoryAdapter);

        updateTypeTabs();
        loadCategories(initialCategoryId);
        tvBillDate.setText(selectedDate);
        updateReceiptPreview();

        setupListeners();

        receiptPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        handleReceiptPicked(uri);
                    }
                });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                cleanupPendingNewReceipt();
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TYPE, currentType);
        outState.putLong(STATE_CATEGORY_ID, categoryAdapter != null ? categoryAdapter.getSelectedCategoryId() : -1L);
        outState.putString(STATE_DATE, selectedDate);
        outState.putString(STATE_CURRENT_RECEIPT, currentReceiptPath);
        outState.putString(STATE_PENDING_RECEIPT, pendingNewReceiptPath);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            cleanupPendingNewReceipt();
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
        if (isFinishing() && !savedSuccessfully) {
            cleanupPendingNewReceipt();
        }
    }

    private void bindViews() {
        tvTabExpense = findViewById(R.id.tv_tab_expense);
        tvTabIncome = findViewById(R.id.tv_tab_income);
        tvCurrencySymbol = findViewById(R.id.tv_currency_symbol);
        etAmount = findViewById(R.id.et_amount);
        btnAddCategory = findViewById(R.id.btn_add_category);
        gvCategories = findViewById(R.id.gv_categories);
        layoutDatePicker = findViewById(R.id.layout_date_picker);
        tvBillDate = findViewById(R.id.tv_bill_date);
        etNote = findViewById(R.id.et_note);
        btnPickReceipt = findViewById(R.id.btn_pick_receipt);
        layoutReceiptPreview = findViewById(R.id.layout_receipt_preview);
        ivReceiptThumb = findViewById(R.id.iv_receipt_thumb);
        tvReceiptPath = findViewById(R.id.tv_receipt_path);
        btnRemoveReceipt = findViewById(R.id.btn_remove_receipt);
        btnSaveBill = findViewById(R.id.btn_save_bill);
        btnDeleteBill = findViewById(R.id.btn_delete_bill);
    }

    private void setupListeners() {
        tvTabExpense.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchType(Category.TYPE_EXPENSE);
            }
        });

        tvTabIncome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchType(Category.TYPE_INCOME);
            }
        });

        gvCategories.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Category category = categoryAdapter.getItem(position);
                if (category != null) {
                    categoryAdapter.setSelectedCategoryId(category.getId());
                }
            }
        });

        gvCategories.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Category category = categoryAdapter.getItem(position);
                if (category == null) {
                    return true;
                }
                if (category.isDefault()) {
                    Toast.makeText(AddEditBillActivity.this, R.string.toast_default_category_undeletable, Toast.LENGTH_SHORT).show();
                    return true;
                }
                showDeleteCategoryDialog(category);
                return true;
            }
        });

        btnAddCategory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddCategoryDialog();
            }
        });

        layoutDatePicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePickerDialog();
            }
        });

        btnPickReceipt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchReceiptPicker();
            }
        });

        ivReceiptThumb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showReceiptZoomDialog();
            }
        });

        btnRemoveReceipt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeCurrentReceipt();
            }
        });

        btnSaveBill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveBill();
            }
        });

        btnDeleteBill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDeleteBill();
            }
        });
    }

    private void switchType(int newType) {
        if (currentType == newType) {
            return;
        }
        currentType = newType;
        updateTypeTabs();
        loadCategories(-1L);
    }

    private void updateTypeTabs() {
        boolean isExpense = currentType == Category.TYPE_EXPENSE;
        int onPrimary = ContextCompat.getColor(this, R.color.textOnPrimary);
        int secondary = ContextCompat.getColor(this, R.color.textSecondary);

        tvTabExpense.setBackgroundResource(isExpense ? R.drawable.bg_tab_selected : R.drawable.bg_tab_unselected);
        tvTabExpense.setTextColor(isExpense ? onPrimary : secondary);

        tvTabIncome.setBackgroundResource(isExpense ? R.drawable.bg_tab_unselected : R.drawable.bg_tab_selected);
        tvTabIncome.setTextColor(isExpense ? secondary : onPrimary);
    }

    private void loadCategories(long preferredCategoryId) {
        List<Category> categories = categoryDao.getByType(currentType);
        categoryAdapter.updateData(categories);

        long resolvedSelection = -1L;
        if (!categories.isEmpty()) {
            if (preferredCategoryId > 0L) {
                for (Category category : categories) {
                    if (category.getId() == preferredCategoryId) {
                        resolvedSelection = preferredCategoryId;
                        break;
                    }
                }
            }
            if (resolvedSelection <= 0L) {
                resolvedSelection = categories.get(0).getId();
            }
        }
        categoryAdapter.setSelectedCategoryId(resolvedSelection);
        UiUtils.setGridViewHeightBasedOnChildren(gvCategories, CATEGORY_COLUMNS);
    }

    private void showAddCategoryDialog() {
        FrameLayout container = new FrameLayout(this);
        int horizontalPadding = UiUtils.dpToPx(this, 20);
        int topPadding = UiUtils.dpToPx(this, 12);
        container.setPadding(horizontalPadding, topPadding, horizontalPadding, 0);

        final EditText input = new EditText(this);
        input.setHint(R.string.hint_new_category);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(currentType == Category.TYPE_INCOME
                        ? R.string.title_new_income_category : R.string.title_new_expense_category)
                .setView(container)
                .setPositiveButton(R.string.action_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) {
                            Toast.makeText(AddEditBillActivity.this, R.string.toast_category_name_empty, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        long newId = categoryDao.addCustomCategory(name, currentType);
                        if (newId == -1L) {
                            Toast.makeText(AddEditBillActivity.this, R.string.toast_category_exists, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(AddEditBillActivity.this,
                                    getString(R.string.fmt_category_added, name), Toast.LENGTH_SHORT).show();
                            loadCategories(newId);
                        }
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showDeleteCategoryDialog(final Category category) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_category_title)
                .setMessage(getString(R.string.fmt_delete_category_message, category.getName()))
                .setPositiveButton(R.string.action_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int result = categoryDao.deleteCustomCategory(category.getId());
                        if (result == CategoryDao.DELETE_OK) {
                            Toast.makeText(AddEditBillActivity.this, R.string.toast_category_deleted, Toast.LENGTH_SHORT).show();
                            long currentSelected = categoryAdapter.getSelectedCategoryId();
                            loadCategories(currentSelected);
                        } else if (result == CategoryDao.DELETE_ERR_IN_USE) {
                            Toast.makeText(AddEditBillActivity.this, R.string.toast_category_in_use, Toast.LENGTH_SHORT).show();
                        } else if (result == CategoryDao.DELETE_ERR_DEFAULT) {
                            Toast.makeText(AddEditBillActivity.this, R.string.toast_default_category_undeletable, Toast.LENGTH_SHORT).show();
                        } else {
                            loadCategories(categoryAdapter.getSelectedCategoryId());
                        }
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showDatePickerDialog() {
        Calendar cal = Calendar.getInstance();
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        sdf.setLenient(false);
        if (selectedDate != null && !selectedDate.trim().isEmpty()) {
            try {
                Date parsed = sdf.parse(selectedDate.trim());
                if (parsed != null) {
                    cal.setTime(parsed);
                }
            } catch (ParseException ignored) {
            }
        }

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        Calendar chosen = Calendar.getInstance();
                        chosen.set(Calendar.YEAR, year);
                        chosen.set(Calendar.MONTH, month);
                        chosen.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        selectedDate = sdf.format(chosen.getTime());
                        tvBillDate.setText(selectedDate);
                    }
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void launchReceiptPicker() {
        try {
            receiptPickerLauncher.launch("image/*");
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.toast_no_image_picker, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleReceiptPicked(Uri uri) {
        if (uri == null || isCopyingReceipt) {
            return;
        }
        isCopyingReceipt = true;
        btnPickReceipt.setEnabled(false);
        btnPickReceipt.setText(R.string.label_saving);
        btnSaveBill.setEnabled(false);
        final Context appContext = getApplicationContext();
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final String copiedPath = FileStorageManager.copyReceiptImage(appContext, uri);
                    final Bitmap thumb = FileStorageManager.decodeSampledBitmap(copiedPath, 360, 360);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isUiInvalid()) {
                                FileStorageManager.deleteReceiptFile(copiedPath);
                                return;
                            }
                            isCopyingReceipt = false;
                            btnPickReceipt.setEnabled(true);
                            btnPickReceipt.setText(R.string.action_pick_image);
                            btnSaveBill.setEnabled(true);
                            if (!pendingNewReceiptPath.isEmpty() && !pendingNewReceiptPath.equals(copiedPath)) {
                                FileStorageManager.deleteReceiptFile(pendingNewReceiptPath);
                            }
                            pendingNewReceiptPath = copiedPath;
                            currentReceiptPath = copiedPath;
                            applyReceiptPreviewBitmap(copiedPath, thumb);
                        }
                    });
                } catch (final IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isUiInvalid()) {
                                return;
                            }
                            isCopyingReceipt = false;
                            btnPickReceipt.setEnabled(true);
                            btnPickReceipt.setText(R.string.action_pick_image);
                            btnSaveBill.setEnabled(true);
                            String msg = e.getMessage();
                            String reason = (msg == null || msg.trim().isEmpty())
                                    ? getString(R.string.error_unable_write_file) : msg;
                            Toast.makeText(
                                    AddEditBillActivity.this,
                                    getString(R.string.fmt_receipt_save_failed, reason),
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    });
                }
            }
        });
    }

    private void updateReceiptPreview() {
        if (currentReceiptPath == null || currentReceiptPath.trim().isEmpty()) {
            layoutReceiptPreview.setVisibility(View.GONE);
            ivReceiptThumb.setImageDrawable(null);
            tvReceiptPath.setText("");
            return;
        }

        layoutReceiptPreview.setVisibility(View.VISIBLE);
        Bitmap thumb = FileStorageManager.decodeSampledBitmap(currentReceiptPath, 360, 360);
        applyReceiptPreviewBitmap(currentReceiptPath, thumb);
    }

    private void applyReceiptPreviewBitmap(String path, Bitmap thumb) {
        layoutReceiptPreview.setVisibility(View.VISIBLE);
        if (thumb != null) {
            ivReceiptThumb.setImageBitmap(thumb);
            File file = new File(path);
            tvReceiptPath.setText(getString(R.string.fmt_receipt_name_path, file.getName(), path));
        } else {
            ivReceiptThumb.setImageResource(android.R.drawable.ic_menu_report_image);
            tvReceiptPath.setText(R.string.receipt_file_missing);
        }
    }

    private boolean isUiInvalid() {
        return isFinishing() || isDestroyed();
    }

    private void showReceiptZoomDialog() {
        if (currentReceiptPath == null || currentReceiptPath.trim().isEmpty()) {
            return;
        }
        Bitmap fullBitmap = FileStorageManager.decodeSampledBitmap(currentReceiptPath, 1080, 1080);
        if (fullBitmap == null) {
            Toast.makeText(this, R.string.receipt_file_missing, Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = UiUtils.dpToPx(this, 16);
        container.setPadding(padding, padding, padding, UiUtils.dpToPx(this, 8));

        ImageView imageView = new ImageView(this);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setMaxHeight(UiUtils.dpToPx(this, 420));
        imageView.setImageBitmap(fullBitmap);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        container.addView(imageView, imgParams);

        TextView pathView = new TextView(this);
        pathView.setText(getString(R.string.fmt_receipt_storage_path, currentReceiptPath));
        pathView.setTextColor(ContextCompat.getColor(this, R.color.textSecondary));
        pathView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textParams.topMargin = UiUtils.dpToPx(this, 10);
        container.addView(pathView, textParams);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_receipt_zoom_title)
                .setView(container)
                .setPositiveButton(R.string.action_close, null)
                .show();
    }

    private void removeCurrentReceipt() {
        cleanupPendingNewReceipt();
        currentReceiptPath = "";
        updateReceiptPreview();
    }

    private void cleanupPendingNewReceipt() {
        if (pendingNewReceiptPath != null && !pendingNewReceiptPath.isEmpty()) {
            FileStorageManager.deleteReceiptFile(pendingNewReceiptPath);
            pendingNewReceiptPath = "";
        }
    }

    private void saveBill() {
        double amount = DateAmountUtils.parsePositiveAmount(etAmount.getText().toString());
        if (amount <= 0.0) {
            etAmount.setError(getString(R.string.error_amount_positive));
            etAmount.requestFocus();
            return;
        }

        long categoryId = categoryAdapter.getSelectedCategoryId();
        if (categoryId <= 0L) {
            Toast.makeText(this, R.string.toast_select_category, Toast.LENGTH_SHORT).show();
            return;
        }

        Bill bill = new Bill();
        bill.setAmount(amount);
        bill.setType(currentType);
        bill.setCategoryId(categoryId);
        bill.setNote(etNote.getText().toString().trim());
        bill.setBillDate(selectedDate);
        bill.setReceiptPath(currentReceiptPath != null ? currentReceiptPath : "");

        if (isEditMode) {
            bill.setId(editBillId);
            bill.setCreatedAt(existingBill.getCreatedAt());
            int updatedRows = billDao.update(bill);
            if (updatedRows > 0) {
                savedSuccessfully = true;
                pendingNewReceiptPath = "";
                if (!originalReceiptPath.isEmpty() && !originalReceiptPath.equals(bill.getReceiptPath())
                        && billDao.countBillsByReceiptPath(originalReceiptPath) == 0) {
                    FileStorageManager.deleteReceiptFile(originalReceiptPath);
                }
                Toast.makeText(this, R.string.toast_bill_updated, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, R.string.toast_save_failed_deleted, Toast.LENGTH_SHORT).show();
            }
        } else {
            bill.setCreatedAt(DateAmountUtils.getCurrentDateTime());
            long insertedId = billDao.insert(bill);
            if (insertedId > 0L) {
                savedSuccessfully = true;
                pendingNewReceiptPath = "";
                Toast.makeText(this, R.string.toast_bill_saved, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, R.string.toast_bill_save_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void confirmDeleteBill() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_bill_title)
                .setMessage(R.string.dialog_delete_bill_message_confirm)
                .setPositiveButton(R.string.action_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int deletedRows = billDao.delete(editBillId);
                        if (deletedRows > 0) {
                            savedSuccessfully = true;
                            cleanupPendingNewReceipt();
                            if (!originalReceiptPath.isEmpty()
                                    && billDao.countBillsByReceiptPath(originalReceiptPath) == 0) {
                                FileStorageManager.deleteReceiptFile(originalReceiptPath);
                            }
                            Toast.makeText(AddEditBillActivity.this, R.string.toast_bill_deleted, Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            Toast.makeText(AddEditBillActivity.this, R.string.toast_bill_not_found, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private static String safeTrim(String text) {
        return text == null ? "" : text.trim();
    }
}
