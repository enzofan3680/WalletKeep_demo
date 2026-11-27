package com.example.walletkeep_demo.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.walletkeep_demo.model.Bill;
import com.example.walletkeep_demo.model.Category;
import com.example.walletkeep_demo.model.CategoryStat;
import com.example.walletkeep_demo.util.DateAmountUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 账单数据访问对象（DAO）。
 */
public class BillDao {
    private static final String SELECT_BILL_WITH_CATEGORY =
            "SELECT b.id, b.amount_cents, b.type, b.category_id, b.note, b.bill_date, b.receipt_path, b.created_at, "
                    + "c.name AS category_name, c.icon AS category_icon "
                    + "FROM " + AccountBookDbHelper.TABLE_BILL + " b "
                    + "LEFT JOIN " + AccountBookDbHelper.TABLE_CATEGORY + " c ON b.category_id = c.id";

    private final Context appContext;
    private final AccountBookDbHelper dbHelper;

    public BillDao(Context context) {
        this(context, AccountBookDbHelper.getInstance(context));
    }

    public BillDao(Context context, AccountBookDbHelper dbHelper) {
        if (context == null || dbHelper == null) {
            throw new IllegalArgumentException("Context and AccountBookDbHelper must not be null");
        }
        Context app = context.getApplicationContext();
        this.appContext = (app != null) ? app : context;
        this.dbHelper = dbHelper;
    }

    public long insert(Bill bill) {
        if (bill.getCreatedAt() == null || bill.getCreatedAt().trim().isEmpty()) {
            bill.setCreatedAt(DateAmountUtils.getCurrentDateTime());
        }
        if (bill.getBillDate() == null || bill.getBillDate().trim().isEmpty()) {
            bill.setBillDate(DateAmountUtils.getCurrentDate());
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.insert(AccountBookDbHelper.TABLE_BILL, null, buildContentValues(bill, true));
    }

    public int update(Bill bill) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.update(
                AccountBookDbHelper.TABLE_BILL,
                buildContentValues(bill, false),
                "id = ?",
                new String[]{String.valueOf(bill.getId())}
        );
    }

    public int delete(long billId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(
                AccountBookDbHelper.TABLE_BILL,
                "id = ?",
                new String[]{String.valueOf(billId)}
        );
    }

    public Bill getById(long billId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    SELECT_BILL_WITH_CATEGORY + " WHERE b.id = ?",
                    new String[]{String.valueOf(billId)}
            );
            if (cursor.moveToFirst()) {
                return billFromCursor(cursor);
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public List<Bill> getRecentBills(int limit) {
        if (limit <= 0) {
            return new ArrayList<Bill>();
        }
        return queryWithRawSql(
                SELECT_BILL_WITH_CATEGORY + " ORDER BY b.bill_date DESC, b.id DESC LIMIT CAST(? AS INTEGER)",
                new String[]{String.valueOf(limit)}
        );
    }

    public List<Bill> getAllBills() {
        return queryWithRawSql(
                SELECT_BILL_WITH_CATEGORY + " ORDER BY b.bill_date DESC, b.id DESC",
                null
        );
    }

    public List<Bill> queryBills(String monthPrefix, int type, long categoryId, String keyword) {
        StringBuilder sql = new StringBuilder(SELECT_BILL_WITH_CATEGORY);
        List<String> conditions = new ArrayList<String>();
        List<String> args = new ArrayList<String>();

        if (monthPrefix != null && !monthPrefix.trim().isEmpty()) {
            conditions.add("b.bill_date LIKE ?");
            args.add(monthPrefix.trim() + "%");
        }
        if (type == Category.TYPE_EXPENSE || type == Category.TYPE_INCOME) {
            conditions.add("b.type = ?");
            args.add(String.valueOf(type));
        }
        if (categoryId > 0) {
            conditions.add("b.category_id = ?");
            args.add(String.valueOf(categoryId));
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            String pattern = "%" + escapeLikePattern(keyword.trim()) + "%";
            conditions.add("(b.note LIKE ? ESCAPE '\\' OR c.name LIKE ? ESCAPE '\\')");
            args.add(pattern);
            args.add(pattern);
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ");
            for (int i = 0; i < conditions.size(); i++) {
                if (i > 0) {
                    sql.append(" AND ");
                }
                sql.append(conditions.get(i));
            }
        }
        sql.append(" ORDER BY b.bill_date DESC, b.id DESC");

        return queryWithRawSql(sql.toString(), args.toArray(new String[0]));
    }

    public double getMonthlyTotal(String monthPrefix, int type) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            String sql;
            String[] args;
            if (monthPrefix != null && !monthPrefix.trim().isEmpty()) {
                sql = "SELECT IFNULL(SUM(amount_cents), 0) FROM " + AccountBookDbHelper.TABLE_BILL
                        + " WHERE type = ? AND bill_date LIKE ?";
                args = new String[]{String.valueOf(type), monthPrefix.trim() + "%"};
            } else {
                sql = "SELECT IFNULL(SUM(amount_cents), 0) FROM " + AccountBookDbHelper.TABLE_BILL
                        + " WHERE type = ?";
                args = new String[]{String.valueOf(type)};
            }
            cursor = db.rawQuery(sql, args);
            if (cursor.moveToFirst()) {
                return cursor.getLong(0) / 100.0;
            }
            return 0.0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public List<CategoryStat> getCategoryStats(String monthPrefix, int type) {
        List<CategoryStat> stats = new ArrayList<CategoryStat>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        double totalSum = 0.0;
        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT b.category_id, c.name AS category_name, c.icon AS category_icon, ")
                    .append("COUNT(b.id) AS bill_count, IFNULL(SUM(b.amount_cents), 0) AS total_amount_cents ")
                    .append("FROM ").append(AccountBookDbHelper.TABLE_BILL).append(" b ")
                    .append("INNER JOIN ").append(AccountBookDbHelper.TABLE_CATEGORY)
                    .append(" c ON b.category_id = c.id ")
                    .append("WHERE b.type = ?");
            List<String> args = new ArrayList<String>();
            args.add(String.valueOf(type));

            if (monthPrefix != null && !monthPrefix.trim().isEmpty()) {
                sql.append(" AND b.bill_date LIKE ?");
                args.add(monthPrefix.trim() + "%");
            }
            sql.append(" GROUP BY b.category_id, c.name, c.icon ORDER BY total_amount_cents DESC");

            cursor = db.rawQuery(sql.toString(), args.toArray(new String[0]));
            while (cursor.moveToNext()) {
                long categoryId = cursor.getLong(cursor.getColumnIndexOrThrow("category_id"));
                String categoryName = cursor.getString(cursor.getColumnIndexOrThrow("category_name"));
                String categoryIcon = cursor.getString(cursor.getColumnIndexOrThrow("category_icon"));
                int billCount = cursor.getInt(cursor.getColumnIndexOrThrow("bill_count"));
                double itemTotal = cursor.getLong(cursor.getColumnIndexOrThrow("total_amount_cents")) / 100.0;
                totalSum += itemTotal;
                stats.add(new CategoryStat(categoryId, categoryName, categoryIcon, billCount, itemTotal, 0.0));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        for (CategoryStat stat : stats) {
            double percentage = totalSum > 0.0 ? (stat.getTotalAmount() * 100.0 / totalSum) : 0.0;
            stat.setPercentage(percentage);
        }
        return stats;
    }

    public List<String> getDistinctMonths() {
        List<String> months = new ArrayList<String>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT DISTINCT substr(bill_date, 1, 7) AS ym FROM " + AccountBookDbHelper.TABLE_BILL
                            + " WHERE length(bill_date) >= 7 ORDER BY ym DESC",
                    null
            );
            while (cursor.moveToNext()) {
                String ym = cursor.getString(0);
                if (ym != null && !ym.trim().isEmpty()) {
                    months.add(ym);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return months;
    }

    public int deleteAllBills() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(AccountBookDbHelper.TABLE_BILL, null, null);
    }

    /**
     * 统计仍引用指定票据路径的账单数量，用于删除账单前判断票据文件是否可安全清理。
     */
    public int countBillsByReceiptPath(String receiptPath) {
        if (receiptPath == null || receiptPath.trim().isEmpty()) {
            return 0;
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM " + AccountBookDbHelper.TABLE_BILL + " WHERE receipt_path = ?",
                    new String[]{receiptPath.trim()}
            );
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public int seedDemoBills() {
        String currentMonth = DateAmountUtils.getCurrentMonth();
        String previousMonth = DateAmountUtils.shiftMonth(currentMonth, -1);

        List<Bill> demoBills = new ArrayList<Bill>();
        demoBills.add(createDemoBill(38.50, Category.TYPE_EXPENSE, "餐饮", "公司楼下午餐", currentMonth + "-15"));
        demoBills.add(createDemoBill(12.00, Category.TYPE_EXPENSE, "交通", "地铁通勤充值", currentMonth + "-14"));
        demoBills.add(createDemoBill(168.00, Category.TYPE_EXPENSE, "购物", "日用品采购", currentMonth + "-11"));
        demoBills.add(createDemoBill(59.00, Category.TYPE_EXPENSE, "学习", "技术书籍购买", currentMonth + "-08"));
        demoBills.add(createDemoBill(8500.00, Category.TYPE_INCOME, "工资", "本月基本工资", currentMonth + "-05"));
        demoBills.add(createDemoBill(215.60, Category.TYPE_INCOME, "理财", "货币基金收益", currentMonth + "-03"));

        demoBills.add(createDemoBill(96.00, Category.TYPE_EXPENSE, "娱乐", "周末电影与爆米花", previousMonth + "-22"));
        demoBills.add(createDemoBill(124.50, Category.TYPE_EXPENSE, "医疗", "药店购药", previousMonth + "-18"));
        demoBills.add(createDemoBill(320.00, Category.TYPE_EXPENSE, "居家", "水电燃气缴费", previousMonth + "-10"));
        demoBills.add(createDemoBill(1200.00, Category.TYPE_INCOME, "兼职", "周末设计外包", previousMonth + "-06"));

        return insertBillsInTransaction(demoBills, new CategoryDao(dbHelper));
    }

    public static final int IMPORT_MODE_REPLACE = 0;
    public static final int IMPORT_MODE_DEDUP_APPEND = 1;

    public static final class ImportResult {
        public final int importedCount;
        public final int skippedCount;
        public final int updatedReceiptCount;
        public final Set<String> attachedReceiptPaths;

        public ImportResult(int importedCount, int skippedCount, int updatedReceiptCount,
                            Set<String> attachedReceiptPaths) {
            this.importedCount = importedCount;
            this.skippedCount = skippedCount;
            this.updatedReceiptCount = updatedReceiptCount;
            this.attachedReceiptPaths = attachedReceiptPaths != null
                    ? Collections.unmodifiableSet(new HashSet<String>(attachedReceiptPaths))
                    : Collections.<String>emptySet();
        }
    }

    private static final class ExistingBillMatch {
        final long id;
        final String receiptPath;

        ExistingBillMatch(long id, String receiptPath) {
            this.id = id;
            this.receiptPath = receiptPath;
        }
    }

    public int insertBillsInTransaction(List<Bill> bills, CategoryDao categoryDao) {
        return importBillsInTransaction(bills, categoryDao, IMPORT_MODE_DEDUP_APPEND).importedCount;
    }

    public ImportResult importBillsInTransaction(List<Bill> bills, CategoryDao categoryDao, int importMode) {
        if (bills == null || bills.isEmpty()) {
            if (importMode == IMPORT_MODE_REPLACE) {
                throw new IllegalArgumentException("备份账单列表为空，已拒绝覆盖清空");
            }
            return new ImportResult(0, 0, 0, Collections.<String>emptySet());
        }
        boolean hasNonNullBill = false;
        for (Bill b : bills) {
            if (b != null) {
                hasNonNullBill = true;
                break;
            }
        }
        if (!hasNonNullBill) {
            if (importMode == IMPORT_MODE_REPLACE) {
                throw new IllegalArgumentException("备份账单列表为空，已拒绝覆盖清空");
            }
            return new ImportResult(0, 0, 0, Collections.<String>emptySet());
        }

        CategoryDao resolver = (categoryDao != null) ? categoryDao : new CategoryDao(dbHelper);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int insertedCount = 0;
        int skippedCount = 0;
        int updatedReceiptCount = 0;
        Set<String> attachedReceiptPaths = new HashSet<String>();
        Map<DuplicateKey, List<ExistingBillMatch>> existingMatches =
                new HashMap<DuplicateKey, List<ExistingBillMatch>>();
        Map<DuplicateKey, Integer> consumedMatches = new HashMap<DuplicateKey, Integer>();
        db.beginTransaction();
        try {
            if (importMode == IMPORT_MODE_REPLACE) {
                db.delete(AccountBookDbHelper.TABLE_BILL, null, null);
            }
            for (Bill bill : bills) {
                if (bill == null) {
                    continue;
                }
                long categoryId = resolver.findOrCreateCategory(db, bill.getCategoryName(), bill.getType());
                if (categoryId <= 0) {
                    throw new IllegalStateException("Unable to resolve category: " + bill.getCategoryName());
                }
                bill.setCategoryId(categoryId);
                if (bill.getCreatedAt() == null || bill.getCreatedAt().trim().isEmpty()) {
                    bill.setCreatedAt(DateAmountUtils.getCurrentDateTime());
                }
                if (bill.getBillDate() == null || bill.getBillDate().trim().isEmpty()) {
                    bill.setBillDate(DateAmountUtils.getCurrentDate());
                }
                String incomingReceipt = bill.getReceiptPath() == null ? "" : bill.getReceiptPath().trim();
                bill.setReceiptPath(incomingReceipt);

                if (importMode == IMPORT_MODE_DEDUP_APPEND) {
                    // 按“内容 + 出现次数”判重：备份里有 2 条完全相同的账单、
                    // 库里只有 1 条时，应补回第 2 条，而不是两条都跳过。
                    DuplicateKey key = new DuplicateKey(bill);
                    List<ExistingBillMatch> matches = existingMatches.get(key);
                    if (matches == null) {
                        matches = findMatchingBills(db, key);
                        existingMatches.put(key, matches);
                    }
                    int used = consumedMatches.containsKey(key) ? consumedMatches.get(key) : 0;
                    if (used < matches.size()) {
                        ExistingBillMatch match = matches.get(used);
                        consumedMatches.put(key, used + 1);
                        skippedCount++;
                        String existingReceipt = match.receiptPath == null ? "" : match.receiptPath.trim();
                        boolean existingValid = !existingReceipt.isEmpty() && new File(existingReceipt).isFile();
                        boolean incomingValid = !incomingReceipt.isEmpty() && new File(incomingReceipt).isFile();
                        if (existingValid) {
                            // 保留已有账单当前实际引用的有效票据（含本次刚从备份快照还原回原路径的图片）
                            attachedReceiptPaths.add(existingReceipt);
                        } else if (incomingValid) {
                            ContentValues patch = new ContentValues();
                            patch.put("receipt_path", incomingReceipt);
                            int rows = db.update(
                                    AccountBookDbHelper.TABLE_BILL,
                                    patch,
                                    "id = ?",
                                    new String[]{String.valueOf(match.id)}
                            );
                            if (rows > 0) {
                                updatedReceiptCount++;
                                attachedReceiptPaths.add(incomingReceipt);
                            }
                        }
                        continue;
                    }
                }
                long rowId = db.insertOrThrow(AccountBookDbHelper.TABLE_BILL, null, buildContentValues(bill, true));
                bill.setId(rowId);
                insertedCount++;
                if (!incomingReceipt.isEmpty()) {
                    attachedReceiptPaths.add(incomingReceipt);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return new ImportResult(insertedCount, skippedCount, updatedReceiptCount, attachedReceiptPaths);
    }

    private List<ExistingBillMatch> findMatchingBills(SQLiteDatabase db, DuplicateKey key) {
        List<ExistingBillMatch> matches = new ArrayList<ExistingBillMatch>();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT id, receipt_path FROM " + AccountBookDbHelper.TABLE_BILL
                            + " WHERE bill_date = ? AND type = ? AND amount_cents = ?"
                            + " AND category_id = ? AND IFNULL(note, '') = ? AND IFNULL(created_at, '') = ?"
                            + " ORDER BY id",
                    new String[]{
                            key.billDate,
                            String.valueOf(key.type),
                            String.valueOf(key.amountCents),
                            String.valueOf(key.categoryId),
                            key.note,
                            key.createdAt
                    }
            );
            while (cursor.moveToNext()) {
                matches.add(new ExistingBillMatch(cursor.getLong(0), cursor.getString(1)));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return matches;
    }

    private static final class DuplicateKey {
        final String billDate;
        final int type;
        final long amountCents;
        final long categoryId;
        final String note;
        final String createdAt;

        DuplicateKey(Bill bill) {
            this.billDate = bill.getBillDate() == null ? "" : bill.getBillDate();
            this.type = bill.getType();
            this.amountCents = DateAmountUtils.toCents(bill.getAmount());
            this.categoryId = bill.getCategoryId();
            this.note = bill.getNote() == null ? "" : bill.getNote();
            this.createdAt = bill.getCreatedAt() == null ? "" : bill.getCreatedAt();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DuplicateKey)) {
                return false;
            }
            DuplicateKey other = (DuplicateKey) o;
            return type == other.type
                    && amountCents == other.amountCents
                    && categoryId == other.categoryId
                    && billDate.equals(other.billDate)
                    && note.equals(other.note)
                    && createdAt.equals(other.createdAt);
        }

        @Override
        public int hashCode() {
            int result = billDate.hashCode();
            result = 31 * result + type;
            result = 31 * result + (int) (amountCents ^ (amountCents >>> 32));
            result = 31 * result + (int) (categoryId ^ (categoryId >>> 32));
            result = 31 * result + note.hashCode();
            result = 31 * result + createdAt.hashCode();
            return result;
        }
    }

    private static String escapeLikePattern(String input) {
        return input.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private List<Bill> queryWithRawSql(String sql, String[] selectionArgs) {
        List<Bill> list = new ArrayList<Bill>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, selectionArgs);
            while (cursor.moveToNext()) {
                list.add(billFromCursor(cursor));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return list;
    }

    private ContentValues buildContentValues(Bill bill, boolean includeCreatedAt) {
        long amountCents = DateAmountUtils.toCents(bill.getAmount());
        if (!DateAmountUtils.isAmountWithinRange(amountCents)) {
            throw new IllegalArgumentException("金额超出允许范围: " + bill.getAmount());
        }
        ContentValues values = new ContentValues();
        values.put("amount_cents", amountCents);
        values.put("type", bill.getType());
        values.put("category_id", bill.getCategoryId());
        values.put("note", bill.getNote());
        values.put("bill_date", bill.getBillDate());
        values.put("receipt_path", bill.getReceiptPath());
        if (includeCreatedAt) {
            values.put("created_at", bill.getCreatedAt());
        }
        return values;
    }

    private Bill createDemoBill(double amount, int type, String categoryName, String note, String billDate) {
        Bill bill = new Bill();
        bill.setAmount(amount);
        bill.setType(type);
        bill.setCategoryName(categoryName);
        bill.setNote(note);
        bill.setBillDate(billDate);
        bill.setReceiptPath("");
        bill.setCreatedAt(DateAmountUtils.getCurrentDateTime());
        return bill;
    }

    private Bill billFromCursor(Cursor cursor) {
        Bill bill = new Bill();
        bill.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
        bill.setAmount(cursor.getLong(cursor.getColumnIndexOrThrow("amount_cents")) / 100.0);
        bill.setType(cursor.getInt(cursor.getColumnIndexOrThrow("type")));
        bill.setCategoryId(cursor.getLong(cursor.getColumnIndexOrThrow("category_id")));
        bill.setNote(cursor.getString(cursor.getColumnIndexOrThrow("note")));
        bill.setBillDate(cursor.getString(cursor.getColumnIndexOrThrow("bill_date")));
        bill.setReceiptPath(cursor.getString(cursor.getColumnIndexOrThrow("receipt_path")));
        bill.setCreatedAt(cursor.getString(cursor.getColumnIndexOrThrow("created_at")));
        bill.setCategoryName(cursor.getString(cursor.getColumnIndexOrThrow("category_name")));
        bill.setCategoryIcon(cursor.getString(cursor.getColumnIndexOrThrow("category_icon")));
        return bill;
    }
}
