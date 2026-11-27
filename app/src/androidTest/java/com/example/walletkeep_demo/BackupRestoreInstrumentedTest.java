package com.example.walletkeep_demo;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.walletkeep_demo.db.AccountBookDbHelper;
import com.example.walletkeep_demo.db.BillDao;
import com.example.walletkeep_demo.db.CategoryDao;
import com.example.walletkeep_demo.model.Bill;
import com.example.walletkeep_demo.model.Category;
import com.example.walletkeep_demo.storage.CsvCodec;
import com.example.walletkeep_demo.storage.FileStorageManager;
import com.example.walletkeep_demo.storage.PrefsManager;
import com.example.walletkeep_demo.util.DateAmountUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 完全隔离的设备端回归测试：使用独立临时数据库、临时文件目录与独立 SharedPreferences，
 * 不会读取、修改或清空目标应用的真实账本数据与票据文件。
 */
@RunWith(AndroidJUnit4.class)
public class BackupRestoreInstrumentedTest {

    private Context targetContext;
    private IsolatedTestContext isolatedContext;
    private String testDbName;
    private String testPrefsPrefix;
    private File isolatedFilesDir;
    private AccountBookDbHelper testDbHelper;
    private BillDao billDao;
    private CategoryDao categoryDao;
    private PrefsManager prefsManager;

    @Before
    public void setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String runId = UUID.randomUUID().toString().replace("-", "");
        testDbName = "test_account_book_" + runId + ".db";
        testPrefsPrefix = "test_walletkeep_" + runId + "_";
        isolatedFilesDir = new File(targetContext.getCacheDir(), "walletkeep_test_files_" + runId);
        assertTrue(isolatedFilesDir.mkdirs() || isolatedFilesDir.isDirectory());

        isolatedContext = new IsolatedTestContext(targetContext, isolatedFilesDir, testPrefsPrefix);
        testDbHelper = new AccountBookDbHelper(isolatedContext, testDbName);
        categoryDao = new CategoryDao(testDbHelper);
        billDao = new BillDao(isolatedContext, testDbHelper);
        prefsManager = new PrefsManager(isolatedContext);
    }

    @After
    public void tearDown() {
        if (testDbHelper != null) {
            testDbHelper.close();
        }
        if (targetContext != null && testDbName != null) {
            targetContext.deleteDatabase(testDbName);
        }
        if (isolatedFilesDir != null) {
            deleteRecursively(isolatedFilesDir);
        }
        if (targetContext != null && testPrefsPrefix != null) {
            String fullPrefName = testPrefsPrefix + PrefsManager.PREF_NAME;
            targetContext.getSharedPreferences(fullPrefName, Context.MODE_PRIVATE).edit().clear().commit();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                targetContext.deleteSharedPreferences(fullPrefName);
            } else {
                File prefFile = new File(
                        targetContext.getApplicationInfo().dataDir + "/shared_prefs/" + fullPrefName + ".xml"
                );
                if (prefFile.exists()) {
                    prefFile.delete();
                }
            }
        }
    }

    @Test
    public void replaceRestore_rejectsZeroByteAndHeaderOnlyCsvWithoutClearingBills() throws Exception {
        Bill bill = createSampleBill(12.34, "");
        billDao.insert(bill);
        assertEquals(1, billDao.getAllBills().size());

        File exportsDir = FileStorageManager.getExportsDir(isolatedContext);
        File emptyFile = new File(exportsDir, "test_zero_byte.csv");
        File headerOnlyFile = new File(exportsDir, "test_header_only.csv");

        FileOutputStream fosEmpty = new FileOutputStream(emptyFile);
        fosEmpty.close();

        FileOutputStream fosHeader = new FileOutputStream(headerOnlyFile);
        fosHeader.write((CsvCodec.HEADER + "\n").getBytes("UTF-8"));
        fosHeader.close();

        try {
            FileStorageManager.importBillsFromCsv(
                    isolatedContext, emptyFile, billDao, categoryDao, BillDao.IMPORT_MODE_REPLACE);
            fail("0 字节 CSV 必须被拒绝");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("空"));
        }

        try {
            FileStorageManager.importBillsFromCsv(
                    isolatedContext, headerOnlyFile, billDao, categoryDao, BillDao.IMPORT_MODE_REPLACE);
            fail("仅表头 CSV 必须被拒绝");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("不包含有效账单"));
        }

        List<Bill> remaining = billDao.getAllBills();
        assertEquals(1, remaining.size());
        assertEquals(12.34, remaining.get(0).getAmount(), 0.001);
    }

    @Test
    public void dedupAppend_backfillsMissingReceiptWhenBillHasEmptyPath() throws Exception {
        File receiptsDir = FileStorageManager.getReceiptsDir(isolatedContext);
        File receiptFile = new File(receiptsDir, "IMG_TEST_BACKFILL.jpg");
        byte[] sampleBytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        writeBytes(receiptFile, sampleBytes);

        Bill original = createSampleBill(12.34, receiptFile.getAbsolutePath());
        billDao.insert(original);

        FileStorageManager.ExportResult fullBackup =
                FileStorageManager.exportBillsBackup(isolatedContext, billDao.getAllBills());
        assertEquals(1, fullBackup.backedUpReceipts);

        // 模拟已有账单缺少票据路径且本地无图片
        billDao.deleteAllBills();
        FileStorageManager.clearAllReceipts(isolatedContext);
        Bill oldWithoutReceipt = createSampleBill(12.34, "");
        oldWithoutReceipt.setCreatedAt(original.getCreatedAt());
        billDao.insert(oldWithoutReceipt);

        FileStorageManager.RestoreResult firstResult = FileStorageManager.importBillsFromCsv(
                isolatedContext, fullBackup.csvFile, billDao, categoryDao, BillDao.IMPORT_MODE_DEDUP_APPEND);

        assertEquals(0, firstResult.importedCount);
        assertEquals(1, firstResult.skippedCount);
        assertEquals(1, firstResult.updatedReceiptCount);
        assertEquals(1, firstResult.restoredReceipts);

        List<Bill> afterBackfill = billDao.getAllBills();
        assertEquals(1, afterBackfill.size());
        File restoredFile = new File(afterBackfill.get(0).getReceiptPath());
        assertTrue(restoredFile.isFile());
        assertArrayEquals(sampleBytes, readBytes(restoredFile));

        // 再次增量去重追加：不应重复关联或虚报还原数量
        FileStorageManager.RestoreResult secondResult = FileStorageManager.importBillsFromCsv(
                isolatedContext, fullBackup.csvFile, billDao, categoryDao, BillDao.IMPORT_MODE_DEDUP_APPEND);
        assertEquals(0, secondResult.importedCount);
        assertEquals(1, secondResult.skippedCount);
        assertEquals(0, secondResult.updatedReceiptCount);
        assertEquals(0, secondResult.restoredReceipts);
    }

    @Test
    public void dedupAppend_restoresMissingReceiptFileWhenBillAlreadyKeepsPath() throws Exception {
        File receiptsDir = FileStorageManager.getReceiptsDir(isolatedContext);
        File receiptFile = new File(receiptsDir, "IMG_TEST_KEEP_PATH.jpg");
        byte[] sampleBytes = new byte[]{10, 20, 30, 40, 50, 60, 70, 80, 90};
        writeBytes(receiptFile, sampleBytes);

        Bill original = createSampleBill(12.34, receiptFile.getAbsolutePath());
        billDao.insert(original);

        FileStorageManager.ExportResult fullBackup =
                FileStorageManager.exportBillsBackup(isolatedContext, billDao.getAllBills());
        assertEquals(1, fullBackup.backedUpReceipts);

        // 仅删除本地票据图片文件，但保留数据库中账单及其原 receipt_path
        assertTrue(receiptFile.delete());
        assertFalse(receiptFile.exists());

        FileStorageManager.RestoreResult restoreResult = FileStorageManager.importBillsFromCsv(
                isolatedContext, fullBackup.csvFile, billDao, categoryDao, BillDao.IMPORT_MODE_DEDUP_APPEND);

        assertEquals(0, restoreResult.importedCount);
        assertEquals(1, restoreResult.skippedCount);
        assertEquals(1, restoreResult.restoredReceipts);

        List<Bill> bills = billDao.getAllBills();
        assertEquals(1, bills.size());
        File restoredFile = new File(bills.get(0).getReceiptPath());
        assertTrue("丢失的票据图片应在提交后保留在磁盘上", restoredFile.isFile());
        assertArrayEquals(sampleBytes, readBytes(restoredFile));

        // 再次执行增量去重追加时，文件已存在，不应再计为新还原
        FileStorageManager.RestoreResult secondResult = FileStorageManager.importBillsFromCsv(
                isolatedContext, fullBackup.csvFile, billDao, categoryDao, BillDao.IMPORT_MODE_DEDUP_APPEND);
        assertEquals(0, secondResult.restoredReceipts);
    }

    @Test
    public void prefsBudgetCents_preservesExactLargeBudget() {
        prefsManager.setMonthlyBudgetCents(13107201L);
        assertEquals(13107201L, prefsManager.getMonthlyBudgetCents());

        DateAmountUtils.BudgetStatus exact = DateAmountUtils.evaluateBudgetCents(
                prefsManager.getMonthlyBudgetCents(), 131072.01d);
        assertEquals(DateAmountUtils.BudgetStatus.LEVEL_WARNING, exact.level);
        assertEquals(0L, exact.overCents);
    }

    @Test
    public void onUpgrade_migratesLegacyAmountToCentsPreservingValue() throws Exception {
        // 手工构造 v1 数据库（amount REAL），再让 SQLiteOpenHelper 触发 v1 -> v2 迁移
        SQLiteDatabase legacy = targetContext.openOrCreateDatabase(testDbName, Context.MODE_PRIVATE, null);
        try {
            legacy.execSQL("CREATE TABLE category ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, type INTEGER NOT NULL, "
                    + "icon TEXT, is_default INTEGER DEFAULT 0)");
            legacy.execSQL("CREATE TABLE bill ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, amount REAL NOT NULL, type INTEGER NOT NULL, "
                    + "category_id INTEGER NOT NULL, note TEXT, bill_date TEXT NOT NULL, receipt_path TEXT, "
                    + "created_at TEXT NOT NULL, FOREIGN KEY(category_id) REFERENCES category(id))");
            legacy.execSQL("INSERT INTO category (id, name, type, icon, is_default) VALUES (1, '餐饮', 0, '餐', 1)");
            legacy.execSQL("INSERT INTO bill (id, amount, type, category_id, note, bill_date, receipt_path, created_at) "
                    + "VALUES (1, 12.34, 0, 1, '午饭', '2026-11-25', '', '2026-11-25 10:00:00')");
            legacy.setVersion(1);
        } finally {
            legacy.close();
        }

        Bill migrated = billDao.getById(1);
        assertNotNull(migrated);
        assertEquals(12.34, migrated.getAmount(), 0.001);
        assertEquals(1234L, DateAmountUtils.toCents(migrated.getAmount()));
        assertEquals(12.34, billDao.getMonthlyTotal("2026-11", Category.TYPE_EXPENSE), 0.0001);
    }

    private Bill createSampleBill(double amount, String receiptPath) {
        List<Category> expenseCats = categoryDao.getByType(Category.TYPE_EXPENSE);
        Bill bill = new Bill();
        bill.setAmount(amount);
        bill.setType(Category.TYPE_EXPENSE);
        bill.setCategoryId(expenseCats.get(0).getId());
        bill.setCategoryName(expenseCats.get(0).getName());
        bill.setNote("回归测试账单");
        bill.setBillDate("2026-11-25");
        bill.setReceiptPath(receiptPath);
        bill.setCreatedAt("2026-11-25 10:00:00");
        return bill;
    }

    private static void writeBytes(File file, byte[] data) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        try {
            fos.write(data);
            fos.flush();
        } finally {
            fos.close();
        }
    }

    private static byte[] readBytes(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        try {
            byte[] buf = new byte[(int) file.length()];
            int offset = 0;
            while (offset < buf.length) {
                int read = fis.read(buf, offset, buf.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            return Arrays.copyOf(buf, offset);
        } finally {
            fis.close();
        }
    }

    private static void deleteRecursively(File target) {
        if (target == null || !target.exists()) {
            return;
        }
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        target.delete();
    }

    private static final class IsolatedTestContext extends ContextWrapper {
        private final File filesDir;
        private final String prefsPrefix;

        IsolatedTestContext(Context base, File filesDir, String prefsPrefix) {
            super(base);
            this.filesDir = filesDir;
            this.prefsPrefix = prefsPrefix;
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public File getFilesDir() {
            if (!filesDir.exists()) {
                filesDir.mkdirs();
            }
            return filesDir;
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return super.getSharedPreferences(prefsPrefix + name, mode);
        }
    }
}
