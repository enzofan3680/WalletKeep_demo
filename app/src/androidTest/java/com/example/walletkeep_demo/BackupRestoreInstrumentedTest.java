package com.example.walletkeep_demo;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

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
import java.io.FileOutputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 覆盖恢复防空文件、去重追加补齐票据关联与预算精确整数分存储的设备端回归测试。
 */
@RunWith(AndroidJUnit4.class)
public class BackupRestoreInstrumentedTest {

    private Context context;
    private BillDao billDao;
    private CategoryDao categoryDao;
    private PrefsManager prefsManager;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        billDao = new BillDao(context);
        categoryDao = new CategoryDao(context);
        prefsManager = new PrefsManager(context);
        billDao.deleteAllBills();
        FileStorageManager.clearAllReceipts(context);
    }

    @After
    public void tearDown() {
        billDao.deleteAllBills();
        FileStorageManager.clearAllReceipts(context);
    }

    @Test
    public void replaceRestore_rejectsZeroByteAndHeaderOnlyCsvWithoutClearingBills() throws Exception {
        Bill bill = createSampleBill(12.34, "");
        billDao.insert(bill);
        assertEquals(1, billDao.getAllBills().size());

        File exportsDir = FileStorageManager.getExportsDir(context);
        File emptyFile = new File(exportsDir, "test_zero_byte.csv");
        File headerOnlyFile = new File(exportsDir, "test_header_only.csv");
        try {
            FileOutputStream fosEmpty = new FileOutputStream(emptyFile);
            fosEmpty.close();

            FileOutputStream fosHeader = new FileOutputStream(headerOnlyFile);
            fosHeader.write((CsvCodec.HEADER + "\n").getBytes("UTF-8"));
            fosHeader.close();

            try {
                FileStorageManager.importBillsFromCsv(
                        context, emptyFile, billDao, categoryDao, BillDao.IMPORT_MODE_REPLACE);
                fail("0 字节 CSV 必须被拒绝");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("空"));
            }

            try {
                FileStorageManager.importBillsFromCsv(
                        context, headerOnlyFile, billDao, categoryDao, BillDao.IMPORT_MODE_REPLACE);
                fail("仅表头 CSV 必须被拒绝");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("不包含有效账单"));
            }

            List<Bill> remaining = billDao.getAllBills();
            assertEquals(1, remaining.size());
            assertEquals(12.34, remaining.get(0).getAmount(), 0.001);
        } finally {
            emptyFile.delete();
            headerOnlyFile.delete();
        }
    }

    @Test
    public void dedupAppend_backfillsMissingReceiptAndCountsAccurately() throws Exception {
        File receiptsDir = FileStorageManager.getReceiptsDir(context);
        File receiptFile = new File(receiptsDir, "IMG_TEST_BACKFILL.jpg");
        byte[] sampleBytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        FileOutputStream fos = new FileOutputStream(receiptFile);
        fos.write(sampleBytes);
        fos.close();

        Bill original = createSampleBill(12.34, receiptFile.getAbsolutePath());
        billDao.insert(original);

        List<Bill> exportedList = billDao.getAllBills();
        FileStorageManager.ExportResult fullBackup = FileStorageManager.exportBillsBackup(context, exportedList);
        assertEquals(1, fullBackup.backedUpReceipts);

        // 模拟先导入缺少票据的旧账单（同时清空本地票据目录）
        billDao.deleteAllBills();
        FileStorageManager.clearAllReceipts(context);
        Bill oldWithoutReceipt = createSampleBill(12.34, "");
        oldWithoutReceipt.setCreatedAt(original.getCreatedAt());
        billDao.insert(oldWithoutReceipt);

        try {
            // 对含原图的新版备份执行“增量去重追加”
            FileStorageManager.RestoreResult firstResult = FileStorageManager.importBillsFromCsv(
                    context, fullBackup.csvFile, billDao, categoryDao, BillDao.IMPORT_MODE_DEDUP_APPEND);

            assertEquals(0, firstResult.importedCount);
            assertEquals(1, firstResult.skippedCount);
            assertEquals(1, firstResult.updatedReceiptCount);
            assertEquals(1, firstResult.restoredReceipts);

            List<Bill> afterBackfill = billDao.getAllBills();
            assertEquals(1, afterBackfill.size());
            String linkedPath = afterBackfill.get(0).getReceiptPath();
            assertTrue(linkedPath != null && !linkedPath.isEmpty());
            File restoredFile = new File(linkedPath);
            assertTrue(restoredFile.isFile());
            assertEquals(sampleBytes.length, restoredFile.length());

            // 再次增量去重追加：不应重复关联或虚报还原数量
            FileStorageManager.RestoreResult secondResult = FileStorageManager.importBillsFromCsv(
                    context, fullBackup.csvFile, billDao, categoryDao, BillDao.IMPORT_MODE_DEDUP_APPEND);
            assertEquals(0, secondResult.importedCount);
            assertEquals(1, secondResult.skippedCount);
            assertEquals(0, secondResult.updatedReceiptCount);
            assertEquals(0, secondResult.restoredReceipts);
        } finally {
            String csvName = fullBackup.csvFile.getName();
            if (csvName.startsWith("bill_backup_") && csvName.endsWith(".csv")) {
                String suffix = csvName.substring("bill_backup_".length(), csvName.length() - ".csv".length());
                File snapDir = new File(fullBackup.csvFile.getParentFile(), "receipts_" + suffix);
                File[] snapFiles = snapDir.listFiles();
                if (snapFiles != null) {
                    for (File sf : snapFiles) {
                        sf.delete();
                    }
                }
                snapDir.delete();
            }
            fullBackup.csvFile.delete();
        }
    }

    @Test
    public void prefsBudgetCents_preservesExactLargeBudget() {
        long originalCents = prefsManager.getMonthlyBudgetCents();
        try {
            prefsManager.setMonthlyBudgetCents(13107201L);
            assertEquals(13107201L, prefsManager.getMonthlyBudgetCents());

            DateAmountUtils.BudgetStatus exact = DateAmountUtils.evaluateBudgetCents(
                    prefsManager.getMonthlyBudgetCents(), 131072.01d);
            assertEquals(DateAmountUtils.BudgetStatus.LEVEL_WARNING, exact.level);
            assertEquals(0L, exact.overCents);
        } finally {
            prefsManager.setMonthlyBudgetCents(originalCents);
        }
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
}
