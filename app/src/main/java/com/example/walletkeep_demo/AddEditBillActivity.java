package com.example.walletkeep_demo;

import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
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
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

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

    private static final int REQUEST_PICK_RECEIPT = 1001;
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
                Toast.makeText(this, "账单不存在或已被删除", Toast.LENGTH_SHORT).show();
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
        btnSaveBill.setText(isEditMode ? "保存修改" : "保存账单");
        btnDeleteBill.setVisibility(isEditMode ? View.VISIBLE : View.GONE);

        categoryAdapter = new CategoryGridAdapter(this, null);
        gvCategories.setAdapter(categoryAdapter);

        updateTypeTabs();
        loadCategories(initialCategoryId);
        tvBillDate.setText(selectedDate);
        updateReceiptPreview();

        setupListeners();
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
    public void onBackPressed() {
        cleanupPendingNewReceipt();
        super.onBackPressed();
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
                    Toast.makeText(AddEditBillActivity.this, "系统默认分类不可删除", Toast.LENGTH_SHORT).show();
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
        setGridViewHeightBasedOnChildren(gvCategories, CATEGORY_COLUMNS);
    }

    private void setGridViewHeightBasedOnChildren(final GridView gridView, final int columns) {
        ListAdapter adapter = gridView.getAdapter();
        if (adapter == null) {
            return;
        }
        int count = adapter.getCount();
        ViewGroup.LayoutParams params = gridView.getLayoutParams();
        if (count == 0) {
            params.height = 0;
            gridView.setLayoutParams(params);
            return;
        }

        final int gridWidth = gridView.getWidth();
        int hSpacing = dpToPx(8);
        int vSpacing = dpToPx(8);

        int itemWidthSpec;
        if (gridWidth > 0) {
            int contentWidth = gridWidth - gridView.getPaddingLeft() - gridView.getPaddingRight()
                    - hSpacing * Math.max(0, columns - 1);
            int colWidth = Math.max(1, contentWidth / columns);
            itemWidthSpec = View.MeasureSpec.makeMeasureSpec(colWidth, View.MeasureSpec.EXACTLY);
        } else {
            itemWidthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        }
        int itemHeightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

        int rows = (count + columns - 1) / columns;
        int totalHeight = gridView.getPaddingTop() + gridView.getPaddingBottom();
        for (int row = 0; row < rows; row++) {
            int rowMaxHeight = 0;
            for (int col = 0; col < columns; col++) {
                int index = row * columns + col;
                if (index >= count) {
                    break;
                }
                View itemView = adapter.getView(index, null, gridView);
                itemView.measure(itemWidthSpec, itemHeightSpec);
                int measuredHeight = itemView.getMeasuredHeight();
                if (measuredHeight > rowMaxHeight) {
                    rowMaxHeight = measuredHeight;
                }
            }
            totalHeight += rowMaxHeight;
        }
        if (rows > 1) {
            totalHeight += vSpacing * (rows - 1);
        }

        params.height = totalHeight;
        gridView.setLayoutParams(params);

        if (gridWidth == 0) {
            gridView.post(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing() && gridView.getWidth() > 0) {
                        setGridViewHeightBasedOnChildren(gridView, columns);
                    }
                }
            });
        }
    }

    private void showAddCategoryDialog() {
        FrameLayout container = new FrameLayout(this);
        int horizontalPadding = dpToPx(20);
        int topPadding = dpToPx(12);
        container.setPadding(horizontalPadding, topPadding, horizontalPadding, 0);

        final EditText input = new EditText(this);
        input.setHint("输入新分类名称，如：奶茶、书籍");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(currentType == Category.TYPE_INCOME ? "新增收入分类" : "新增支出分类")
                .setView(container)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) {
                            Toast.makeText(AddEditBillActivity.this, "分类名称不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        long newId = categoryDao.addCustomCategory(name, currentType);
                        if (newId == -1L) {
                            Toast.makeText(AddEditBillActivity.this, "该分类已存在", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(AddEditBillActivity.this, "已添加分类「" + name + "」", Toast.LENGTH_SHORT).show();
                            loadCategories(newId);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteCategoryDialog(final Category category) {
        new AlertDialog.Builder(this)
                .setTitle("删除分类")
                .setMessage("确定删除自定义分类「" + category.getName() + "」吗？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int result = categoryDao.deleteCustomCategory(category.getId());
                        if (result == CategoryDao.DELETE_OK) {
                            Toast.makeText(AddEditBillActivity.this, "已删除分类", Toast.LENGTH_SHORT).show();
                            long currentSelected = categoryAdapter.getSelectedCategoryId();
                            loadCategories(currentSelected);
                        } else if (result == CategoryDao.DELETE_ERR_IN_USE) {
                            Toast.makeText(AddEditBillActivity.this, "该分类下已有账单记录，无法删除", Toast.LENGTH_SHORT).show();
                        } else if (result == CategoryDao.DELETE_ERR_DEFAULT) {
                            Toast.makeText(AddEditBillActivity.this, "系统默认分类不可删除", Toast.LENGTH_SHORT).show();
                        } else {
                            loadCategories(categoryAdapter.getSelectedCategoryId());
                        }
                    }
                })
                .setNegativeButton("取消", null)
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
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "选择票据图片"), REQUEST_PICK_RECEIPT);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "未找到可用的图片选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_RECEIPT && resultCode == RESULT_OK && data != null) {
            final Uri uri = data.getData();
            if (uri == null || isCopyingReceipt) {
                return;
            }
            isCopyingReceipt = true;
            btnPickReceipt.setEnabled(false);
            btnPickReceipt.setText("正在保存...");
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
                                btnPickReceipt.setText("选择图片");
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
                                btnPickReceipt.setText("选择图片");
                                btnSaveBill.setEnabled(true);
                                String msg = e.getMessage();
                                Toast.makeText(
                                        AddEditBillActivity.this,
                                        "保存图片失败：" + (msg == null || msg.trim().isEmpty() ? "无法写入文件" : msg),
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        });
                    }
                }
            });
        }
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
            tvReceiptPath.setText("原票据文件不存在");
        }
    }

    private boolean isUiInvalid() {
        if (isFinishing()) {
            return true;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed();
    }

    private void showReceiptZoomDialog() {
        if (currentReceiptPath == null || currentReceiptPath.trim().isEmpty()) {
            return;
        }
        Bitmap fullBitmap = FileStorageManager.decodeSampledBitmap(currentReceiptPath, 1080, 1080);
        if (fullBitmap == null) {
            Toast.makeText(this, "原票据文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPx(16);
        container.setPadding(padding, padding, padding, dpToPx(8));

        ImageView imageView = new ImageView(this);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setMaxHeight(dpToPx(420));
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
        textParams.topMargin = dpToPx(10);
        container.addView(pathView, textParams);

        new AlertDialog.Builder(this)
                .setTitle("票据大图预览")
                .setView(container)
                .setPositiveButton("关闭", null)
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
            etAmount.setError("请输入大于 0 的有效金额");
            etAmount.requestFocus();
            return;
        }

        long categoryId = categoryAdapter.getSelectedCategoryId();
        if (categoryId <= 0L) {
            Toast.makeText(this, "请先选择账单分类", Toast.LENGTH_SHORT).show();
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
                if (!originalReceiptPath.isEmpty() && !originalReceiptPath.equals(bill.getReceiptPath())) {
                    FileStorageManager.deleteReceiptFile(originalReceiptPath);
                }
                Toast.makeText(this, "修改已保存", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, "保存失败，账单可能已被删除", Toast.LENGTH_SHORT).show();
            }
        } else {
            bill.setCreatedAt(DateAmountUtils.getCurrentDateTime());
            long insertedId = billDao.insert(bill);
            if (insertedId > 0L) {
                savedSuccessfully = true;
                pendingNewReceiptPath = "";
                Toast.makeText(this, "记账成功", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, "保存账单失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void confirmDeleteBill() {
        new AlertDialog.Builder(this)
                .setTitle("删除账单")
                .setMessage("确定删除这笔账单吗？删除后无法恢复。")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int deletedRows = billDao.delete(editBillId);
                        if (deletedRows > 0) {
                            savedSuccessfully = true;
                            cleanupPendingNewReceipt();
                            if (!originalReceiptPath.isEmpty()) {
                                FileStorageManager.deleteReceiptFile(originalReceiptPath);
                            }
                            Toast.makeText(AddEditBillActivity.this, "账单已删除", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            Toast.makeText(AddEditBillActivity.this, "账单不存在或已被删除", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        ));
    }

    private static String safeTrim(String text) {
        return text == null ? "" : text.trim();
    }
}
