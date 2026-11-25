package com.example.walletkeep_demo.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.walletkeep_demo.model.Bill;
import com.example.walletkeep_demo.model.Category;
import com.example.walletkeep_demo.model.CategoryStat;
import com.example.walletkeep_demo.util.DateAmountUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 账单数据访问对象（DAO）。
 */
public class BillDao {
    private static final String SELECT_BILL_WITH_CATEGORY =
            "SELECT b.id, b.amount, b.type, b.category_id, b.note, b.bill_date, b.receipt_path, b.created_at, "
                    + "c.name AS category_name, c.icon AS category_icon "
                    + "FROM " + AccountBookDbHelper.TABLE_BILL + " b "
                    + "LEFT JOIN " + AccountBookDbHelper.TABLE_CATEGORY + " c ON b.category_id = c.id";

    private final Context appContext;
    private final AccountBookDbHelper dbHelper;

    public BillDao(Context context) {
        this.appContext = context.getApplicationContext();
        this.dbHelper = AccountBookDbHelper.getInstance(this.appContext);
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
            String pattern = "%" + keyword.trim() + "%";
            conditions.add("(b.note LIKE ? OR c.name LIKE ?)");
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
                sql = "SELECT IFNULL(SUM(amount), 0) FROM " + AccountBookDbHelper.TABLE_BILL
                        + " WHERE type = ? AND bill_date LIKE ?";
                args = new String[]{String.valueOf(type), monthPrefix.trim() + "%"};
            } else {
                sql = "SELECT IFNULL(SUM(amount), 0) FROM " + AccountBookDbHelper.TABLE_BILL
                        + " WHERE type = ?";
                args = new String[]{String.valueOf(type)};
            }
            cursor = db.rawQuery(sql, args);
            if (cursor.moveToFirst()) {
                return cursor.getDouble(0);
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
                    .append("COUNT(b.id) AS bill_count, IFNULL(SUM(b.amount), 0) AS total_amount ")
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
            sql.append(" GROUP BY b.category_id, c.name, c.icon ORDER BY total_amount DESC");

            cursor = db.rawQuery(sql.toString(), args.toArray(new String[0]));
            while (cursor.moveToNext()) {
                long categoryId = cursor.getLong(cursor.getColumnIndexOrThrow("category_id"));
                String categoryName = cursor.getString(cursor.getColumnIndexOrThrow("category_name"));
                String categoryIcon = cursor.getString(cursor.getColumnIndexOrThrow("category_icon"));
                int billCount = cursor.getInt(cursor.getColumnIndexOrThrow("bill_count"));
                double itemTotal = cursor.getDouble(cursor.getColumnIndexOrThrow("total_amount"));
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

        return insertBillsInTransaction(demoBills, new CategoryDao(appContext));
    }

    public static final int IMPORT_MODE_REPLACE = 0;
    public static final int IMPORT_MODE_DEDUP_APPEND = 1;

    public static final class ImportResult {
        public final int importedCount;
        public final int skippedCount;

        public ImportResult(int importedCount, int skippedCount) {
            this.importedCount = importedCount;
            this.skippedCount = skippedCount;
        }
    }

    public int insertBillsInTransaction(List<Bill> bills, CategoryDao categoryDao) {
        return importBillsInTransaction(bills, categoryDao, IMPORT_MODE_DEDUP_APPEND).importedCount;
    }

    public ImportResult importBillsInTransaction(List<Bill> bills, CategoryDao categoryDao, int importMode) {
        if (bills == null) {
            return new ImportResult(0, 0);
        }
        if (bills.isEmpty() && importMode != IMPORT_MODE_REPLACE) {
            return new ImportResult(0, 0);
        }
        CategoryDao resolver = (categoryDao != null) ? categoryDao : new CategoryDao(appContext);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int insertedCount = 0;
        int skippedCount = 0;
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
                if (importMode == IMPORT_MODE_DEDUP_APPEND && existsDuplicateBill(db, bill)) {
                    skippedCount++;
                    continue;
                }
                long rowId = db.insertOrThrow(AccountBookDbHelper.TABLE_BILL, null, buildContentValues(bill, true));
                bill.setId(rowId);
                insertedCount++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return new ImportResult(insertedCount, skippedCount);
    }

    private boolean existsDuplicateBill(SQLiteDatabase db, Bill bill) {
        Cursor cursor = null;
        try {
            String note = bill.getNote() == null ? "" : bill.getNote();
            String createdAt = bill.getCreatedAt() == null ? "" : bill.getCreatedAt();
            cursor = db.rawQuery(
                    "SELECT id FROM " + AccountBookDbHelper.TABLE_BILL
                            + " WHERE bill_date = ? AND type = ? AND ABS(amount - ?) < 0.005"
                            + " AND category_id = ? AND IFNULL(note, '') = ? AND IFNULL(created_at, '') = ?"
                            + " LIMIT 1",
                    new String[]{
                            bill.getBillDate(),
                            String.valueOf(bill.getType()),
                            String.valueOf(bill.getAmount()),
                            String.valueOf(bill.getCategoryId()),
                            note,
                            createdAt
                    }
            );
            return cursor.moveToFirst();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
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
        ContentValues values = new ContentValues();
        values.put("amount", bill.getAmount());
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
        bill.setAmount(cursor.getDouble(cursor.getColumnIndexOrThrow("amount")));
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
