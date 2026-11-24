# 《记账小助手》(WalletKeep) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete, offline Android personal bookkeeping application (**WalletKeep / 记账小助手**) in `D:\26term-android-homework\WalletKeep_demo` demonstrating Android SQLite (2 relational tables), File Storage (CSV export/import + receipt image storage), and SharedPreferences across 6 functional modules and 5 Activity screens.

**Architecture:** Clean layered Java Android app (`model`, `db`, `storage`, `ui/adapter`, `ui/view`, `util`, and 5 root Activities) backed by `SQLiteOpenHelper` (`account_book.db`), app-private `filesDir` storage (`exports/` and `receipts/`), and `SharedPreferences` (`app_settings`). UI uses native Android XML views (`ListView`, `GridView`, `ScrollView`, custom 2D `Canvas` `CategoryChartView`) with zero third-party dependency overhead and DayNight theme support.

**Tech Stack:** Java 8 (`D:\android-studio-ide\jre`), Android Gradle Plugin `3.6.3`, Gradle `5.6.4`, `compileSdkVersion 29`, `buildToolsVersion "28.0.3"`, `minSdkVersion 17`, `targetSdkVersion 29`, `androidx.appcompat:appcompat:1.0.2`, `androidx.constraintlayout:constraintlayout:1.1.3`, `junit:junit:4.12`.

## Global Constraints

- Project root: `D:\26term-android-homework\WalletKeep_demo`
- Package name (`applicationId`): `com.example.walletkeep_demo`
- All Gradle CLI commands MUST run with JDK 8: `$env:JAVA_HOME="D:\android-studio-ide\jre"` inside `D:\26term-android-homework\WalletKeep_demo`
- `minSdkVersion 17`: Never use `java.time.*` or `StandardCharsets` (API 19+); always use `Calendar`/`SimpleDateFormat` and `"UTF-8"` charset name.
- No runtime storage permissions: All CSV exports (`files/exports/`) and receipt images (`files/receipts/`) MUST use `context.getFilesDir()`.
- All UI labels, prompts, and category names are in Simplified Chinese matching the design specification (`docs/superpowers/specs/2026-11-24-walletkeep-account-book-design.md`).
- Every Activity refreshes its display from `BillDao`/`CategoryDao`/`PrefsManager` in `onResume()` to guarantee immediate state consistency after returning from child screens.

---

### Task 1: Build Configuration, Theme Resources, Models & Tested Pure-Java Utilities

**Files:**
- Modify: `D:\26term-android-homework\WalletKeep_demo\build.gradle`
- Modify: `D:\26term-android-homework\WalletKeep_demo\app\build.gradle`
- Modify: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\values\colors.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\values-night\colors.xml`
- Modify: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\values\strings.xml`
- Modify: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\values\styles.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\drawable\bg_card.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\drawable\bg_hero_card.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\drawable\bg_button_primary.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\drawable\bg_button_danger.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\drawable\bg_button_outline.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\drawable\bg_input.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\drawable\bg_circle_badge.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\drawable\bg_fab.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\drawable\bg_tab_selected.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\drawable\bg_tab_unselected.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\model\Category.java`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\model\Bill.java`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\model\CategoryStat.java`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\util\DateAmountUtils.java`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\storage\CsvCodec.java`
- Test: `D:\26term-android-homework\WalletKeep_demo\app\src\test\java\com\example\walletkeep_demo\DateAmountUtilsTest.java`
- Test: `D:\26term-android-homework\WalletKeep_demo\app\src\test\java\com\example\walletkeep_demo\CsvCodecTest.java`

**Interfaces:**
- Consumes: None (foundation task).
- Produces:
  - `Category(long id, String name, int type, String icon, boolean isDefault)` with constants `TYPE_EXPENSE = 0`, `TYPE_INCOME = 1`.
  - `Bill` POJO with getters/setters for `id`, `amount`, `type`, `categoryId`, `categoryName`, `categoryIcon`, `note`, `billDate`, `receiptPath`, `createdAt`.
  - `CategoryStat(long categoryId, String categoryName, String categoryIcon, int billCount, double totalAmount, double percentage)`.
  - `DateAmountUtils`: `getCurrentDate()`, `getCurrentMonth()`, `getCurrentDateTime()`, `shiftMonth(String yyyyMM, int deltaMonths)`, `formatAmount(String currency, double amount)`, `formatSignedAmount(String currency, int type, double amount)`, `parsePositiveAmount(String raw)`.
  - `CsvCodec`: `HEADER`, `formatBills(List<Bill> bills)`, `parseBills(String csvContent)` returning `List<Bill>`.

- [ ] **Step 1: Align `build.gradle` and `app/build.gradle` for offline AGP 3.6.3 / SDK 29 builds**

Update `D:\26term-android-homework\WalletKeep_demo\build.gradle`:
```groovy
buildscript {
    repositories {
        google()
        mavenCentral()
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:3.6.3'
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        jcenter()
    }
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
```

Update `D:\26term-android-homework\WalletKeep_demo\app\build.gradle`:
```groovy
apply plugin: 'com.android.application'

android {
    compileSdkVersion 29
    buildToolsVersion "28.0.3"

    defaultConfig {
        applicationId "com.example.walletkeep_demo"
        minSdkVersion 17
        targetSdkVersion 29
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}

dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar'])

    implementation 'androidx.appcompat:appcompat:1.0.2'
    implementation 'androidx.constraintlayout:constraintlayout:1.1.3'
    testImplementation 'junit:junit:4.12'
    androidTestImplementation 'androidx.test.ext:junit:1.1.1'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.2.0'
}
```

- [ ] **Step 2: Create light & dark color palettes, strings, styles, and shape drawables**

Write `res/values/colors.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="colorPrimary">#1E88E5</color>
    <color name="colorPrimaryDark">#1565C0</color>
    <color name="colorAccent">#FF7043</color>

    <color name="bgPage">#F4F6F9</color>
    <color name="bgCard">#FFFFFF</color>
    <color name="bgInput">#F8FAFC</color>
    <color name="bgTag">#EBF3FE</color>
    <color name="dividerColor">#E2E8F0</color>

    <color name="textPrimary">#1E293B</color>
    <color name="textSecondary">#64748B</color>
    <color name="textOnPrimary">#FFFFFF</color>

    <color name="colorExpense">#E53935</color>
    <color name="colorIncome">#2E7D32</color>
    <color name="colorWarning">#FB8C00</color>
    <color name="colorDanger">#D32F2F</color>
    <color name="colorSafe">#43A047</color>
</resources>
```

Write `res/values-night/colors.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="colorPrimary">#42A5F5</color>
    <color name="colorPrimaryDark">#1E88E5</color>
    <color name="colorAccent">#FF8A65</color>

    <color name="bgPage">#12161C</color>
    <color name="bgCard">#1E2530</color>
    <color name="bgInput">#27303F</color>
    <color name="bgTag">#23374D</color>
    <color name="dividerColor">#2E3A4B</color>

    <color name="textPrimary">#F1F5F9</color>
    <color name="textSecondary">#94A3B8</color>
    <color name="textOnPrimary">#FFFFFF</color>

    <color name="colorExpense">#EF5350</color>
    <color name="colorIncome">#66BB6A</color>
    <color name="colorWarning">#FFA726</color>
    <color name="colorDanger">#EF5350</color>
    <color name="colorSafe">#66BB6A</color>
</resources>
```

Write `res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">记账小助手</string>
    <string name="title_add_bill">记一笔</string>
    <string name="title_edit_bill">编辑账单</string>
    <string name="title_bill_list">账单明细</string>
    <string name="title_stats">收支统计</string>
    <string name="title_settings">预算与备份设置</string>
</resources>
```

Write `res/values/styles.xml`:
```xml
<resources>
    <style name="AppTheme" parent="Theme.AppCompat.DayNight.DarkActionBar">
        <item name="colorPrimary">@color/colorPrimary</item>
        <item name="colorPrimaryDark">@color/colorPrimaryDark</item>
        <item name="colorAccent">@color/colorAccent</item>
    </style>
</resources>
```

Create drawable XMLs (`bg_card.xml`, `bg_hero_card.xml`, `bg_button_primary.xml`, `bg_button_danger.xml`, `bg_button_outline.xml`, `bg_input.xml`, `bg_circle_badge.xml`, `bg_fab.xml`, `bg_tab_selected.xml`, `bg_tab_unselected.xml`) using clean `<shape>` definitions referencing the semantic color tokens above.

- [ ] **Step 3: Create model POJOs (`Category.java`, `Bill.java`, `CategoryStat.java`)**

Create `model/Category.java`:
```java
package com.example.walletkeep_demo.model;

public class Category {
    public static final int TYPE_EXPENSE = 0;
    public static final int TYPE_INCOME = 1;

    private long id;
    private String name;
    private int type;
    private String icon;
    private boolean isDefault;

    public Category() {
    }

    public Category(long id, String name, int type, String icon, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.icon = icon;
        this.isDefault = isDefault;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    @Override
    public String toString() {
        return name == null ? "" : name;
    }
}
```

Create `model/Bill.java`:
```java
package com.example.walletkeep_demo.model;

public class Bill {
    private long id;
    private double amount;
    private int type;
    private long categoryId;
    private String categoryName;
    private String categoryIcon;
    private String note;
    private String billDate;
    private String receiptPath;
    private String createdAt;

    public Bill() {
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public long getCategoryId() { return categoryId; }
    public void setCategoryId(long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getCategoryIcon() { return categoryIcon; }
    public void setCategoryIcon(String categoryIcon) { this.categoryIcon = categoryIcon; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getBillDate() { return billDate; }
    public void setBillDate(String billDate) { this.billDate = billDate; }

    public String getReceiptPath() { return receiptPath; }
    public void setReceiptPath(String receiptPath) { this.receiptPath = receiptPath; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
```

Create `model/CategoryStat.java`:
```java
package com.example.walletkeep_demo.model;

public class CategoryStat {
    private long categoryId;
    private String categoryName;
    private String categoryIcon;
    private int billCount;
    private double totalAmount;
    private double percentage;

    public CategoryStat(long categoryId, String categoryName, String categoryIcon,
                        int billCount, double totalAmount, double percentage) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.categoryIcon = categoryIcon;
        this.billCount = billCount;
        this.totalAmount = totalAmount;
        this.percentage = percentage;
    }

    public long getCategoryId() { return categoryId; }
    public String getCategoryName() { return categoryName; }
    public String getCategoryIcon() { return categoryIcon; }
    public int getBillCount() { return billCount; }
    public double getTotalAmount() { return totalAmount; }
    public double getPercentage() { return percentage; }
    public void setPercentage(double percentage) { this.percentage = percentage; }
}
```

- [ ] **Step 4: Write failing unit tests for `DateAmountUtils` and `CsvCodec`**

Create `app/src/test/java/com/example/walletkeep_demo/DateAmountUtilsTest.java`:
```java
package com.example.walletkeep_demo;

import com.example.walletkeep_demo.model.Category;
import com.example.walletkeep_demo.util.DateAmountUtils;
import org.junit.Test;
import static org.junit.Assert.*;

public class DateAmountUtilsTest {
    @Test
    public void shiftMonth_handlesYearRollover() {
        assertEquals("2026-12", DateAmountUtils.shiftMonth("2026-11", 1));
        assertEquals("2027-01", DateAmountUtils.shiftMonth("2026-12", 1));
        assertEquals("2025-12", DateAmountUtils.shiftMonth("2026-01", -1));
    }

    @Test
    public void formatAndParseAmount_validatesCorrectly() {
        assertEquals("¥12.50", DateAmountUtils.formatAmount("¥", 12.5));
        assertEquals("-¥88.00", DateAmountUtils.formatSignedAmount("¥", Category.TYPE_EXPENSE, 88.0));
        assertEquals("+$500.25", DateAmountUtils.formatSignedAmount("$", Category.TYPE_INCOME, 500.25));
        assertEquals(25.5, DateAmountUtils.parsePositiveAmount("25.5"), 0.001);
        assertEquals(-1.0, DateAmountUtils.parsePositiveAmount("0"), 0.001);
        assertEquals(-1.0, DateAmountUtils.parsePositiveAmount("-5"), 0.001);
        assertEquals(-1.0, DateAmountUtils.parsePositiveAmount("abc"), 0.001);
    }
}
```

Create `app/src/test/java/com/example/walletkeep_demo/CsvCodecTest.java`:
```java
package com.example.walletkeep_demo;

import com.example.walletkeep_demo.model.Bill;
import com.example.walletkeep_demo.model.Category;
import com.example.walletkeep_demo.storage.CsvCodec;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class CsvCodecTest {
    @Test
    public void roundTrip_preservesCommasQuotesAndEmptyFields() {
        Bill b1 = new Bill();
        b1.setId(1);
        b1.setAmount(38.50);
        b1.setType(Category.TYPE_EXPENSE);
        b1.setCategoryName("餐饮");
        b1.setNote("牛肉面, 加蛋 \"特辣\"");
        b1.setBillDate("2026-11-24");
        b1.setReceiptPath("/data/user/0/app/files/receipts/IMG_1.jpg");
        b1.setCreatedAt("2026-11-24 12:30:00");

        Bill b2 = new Bill();
        b2.setId(2);
        b2.setAmount(5000.00);
        b2.setType(Category.TYPE_INCOME);
        b2.setCategoryName("工资");
        b2.setNote("");
        b2.setBillDate("2026-11-01");
        b2.setReceiptPath("");
        b2.setCreatedAt("2026-11-01 09:00:00");

        String csv = CsvCodec.formatBills(Arrays.asList(b1, b2));
        List<Bill> parsed = CsvCodec.parseBills(csv);

        assertEquals(2, parsed.size());
        assertEquals(38.50, parsed.get(0).getAmount(), 0.001);
        assertEquals(Category.TYPE_EXPENSE, parsed.get(0).getType());
        assertEquals("餐饮", parsed.get(0).getCategoryName());
        assertEquals("牛肉面, 加蛋 \"特辣\"", parsed.get(0).getNote());
        assertEquals("2026-11-24", parsed.get(0).getBillDate());
        assertEquals("/data/user/0/app/files/receipts/IMG_1.jpg", parsed.get(0).getReceiptPath());

        assertEquals(5000.00, parsed.get(1).getAmount(), 0.001);
        assertEquals(Category.TYPE_INCOME, parsed.get(1).getType());
        assertEquals("工资", parsed.get(1).getCategoryName());
        assertEquals("", parsed.get(1).getNote());
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseBills_rejectsInvalidAmountRow() {
        String badCsv = CsvCodec.HEADER + "\n1,not_a_number,0,餐饮,午饭,2026-11-24,,2026-11-24 12:00:00\n";
        CsvCodec.parseBills(badCsv);
    }
}
```

- [ ] **Step 5: Run tests to verify failure**

Run: `$env:JAVA_HOME="D:\android-studio-ide\jre"; .\gradlew.bat testDebugUnitTest`
Expected: FAIL (`cannot find symbol class DateAmountUtils` / `CsvCodec`).

- [ ] **Step 6: Implement `DateAmountUtils.java` and `CsvCodec.java`**

Create `util/DateAmountUtils.java`:
```java
package com.example.walletkeep_demo.util;

import com.example.walletkeep_demo.model.Category;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class DateAmountUtils {
    private DateAmountUtils() {}

    public static String getCurrentDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
    }

    public static String getCurrentMonth() {
        return new SimpleDateFormat("yyyy-MM", Locale.CHINA).format(new Date());
    }

    public static String getCurrentDateTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
    }

    public static String shiftMonth(String yyyyMM, int deltaMonths) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM", Locale.CHINA);
        Calendar cal = Calendar.getInstance();
        try {
            Date date = fmt.parse(yyyyMM);
            if (date != null) {
                cal.setTime(date);
            }
        } catch (ParseException ignored) {
        }
        cal.add(Calendar.MONTH, deltaMonths);
        return fmt.format(cal.getTime());
    }

    public static String formatAmount(String currency, double amount) {
        String symbol = (currency == null || currency.isEmpty()) ? "¥" : currency;
        return String.format(Locale.CHINA, "%s%.2f", symbol, amount);
    }

    public static String formatSignedAmount(String currency, int type, double amount) {
        String prefix = (type == Category.TYPE_INCOME) ? "+" : "-";
        return prefix + formatAmount(currency, amount);
    }

    public static double parsePositiveAmount(String raw) {
        if (raw == null) {
            return -1.0;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return -1.0;
        }
        try {
            double val = Double.parseDouble(trimmed);
            if (Double.isNaN(val) || Double.isInfinite(val) || val <= 0.0) {
                return -1.0;
            }
            return Math.round(val * 100.0) / 100.0;
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }
}
```

Create `storage/CsvCodec.java`:
```java
package com.example.walletkeep_demo.storage;

import com.example.walletkeep_demo.model.Bill;
import com.example.walletkeep_demo.model.Category;
import com.example.walletkeep_demo.util.DateAmountUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CsvCodec {
    public static final String HEADER = "id,amount,type,category,note,bill_date,receipt_path,created_at";

    private CsvCodec() {}

    public static String formatBills(List<Bill> bills) {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append("\n");
        if (bills != null) {
            for (Bill b : bills) {
                sb.append(b.getId()).append(",")
                  .append(String.format(Locale.US, "%.2f", b.getAmount())).append(",")
                  .append(b.getType()).append(",")
                  .append(escapeField(b.getCategoryName())).append(",")
                  .append(escapeField(b.getNote())).append(",")
                  .append(escapeField(b.getBillDate())).append(",")
                  .append(escapeField(b.getReceiptPath())).append(",")
                  .append(escapeField(b.getCreatedAt())).append("\n");
            }
        }
        return sb.toString();
    }

    public static List<Bill> parseBills(String csvContent) {
        List<Bill> result = new ArrayList<Bill>();
        if (csvContent == null || csvContent.trim().isEmpty()) {
            return result;
        }
        List<List<String>> rows = parseRows(csvContent);
        if (rows.isEmpty()) {
            return result;
        }
        int startIndex = 0;
        List<String> firstRow = rows.get(0);
        if (!firstRow.isEmpty() && "id".equalsIgnoreCase(firstRow.get(0).trim().replace("\uFEFF", ""))) {
            startIndex = 1;
        }
        for (int i = startIndex; i < rows.size(); i++) {
            List<String> cols = rows.get(i);
            if (cols.size() == 1 && cols.get(0).trim().isEmpty()) {
                continue;
            }
            if (cols.size() < 6) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行字段数不足");
            }
            double amount = DateAmountUtils.parsePositiveAmount(cols.get(1));
            if (amount <= 0) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行金额不合法: " + cols.get(1));
            }
            int type;
            try {
                type = Integer.parseInt(cols.get(2).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行收支类型不合法: " + cols.get(2));
            }
            if (type != Category.TYPE_EXPENSE && type != Category.TYPE_INCOME) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行收支类型必须为 0 或 1");
            }
            String categoryName = cols.get(3).trim();
            if (categoryName.isEmpty()) {
                categoryName = (type == Category.TYPE_EXPENSE) ? "其他支出" : "其他收入";
            }
            String note = cols.get(4);
            String billDate = cols.get(5).trim();
            if (billDate.isEmpty()) {
                billDate = DateAmountUtils.getCurrentDate();
            }
            String receiptPath = cols.size() > 6 ? cols.get(6).trim() : "";
            String createdAt = cols.size() > 7 && !cols.get(7).trim().isEmpty()
                    ? cols.get(7).trim()
                    : DateAmountUtils.getCurrentDateTime();

            Bill bill = new Bill();
            bill.setAmount(amount);
            bill.setType(type);
            bill.setCategoryName(categoryName);
            bill.setNote(note);
            bill.setBillDate(billDate);
            bill.setReceiptPath(receiptPath);
            bill.setCreatedAt(createdAt);
            result.add(bill);
        }
        return result;
    }

    private static String escapeField(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        if (!needsQuotes) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static List<List<String>> parseRows(String content) {
        List<List<String>> rows = new ArrayList<List<String>>();
        List<String> currentRow = new ArrayList<String>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        int len = content.length();
        for (int i = 0; i < len; i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && content.charAt(i + 1) == '"') {
                        currentField.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    currentField.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    currentRow.add(currentField.toString());
                    currentField.setLength(0);
                } else if (c == '\r') {
                    // ignore CR, handle LF
                } else if (c == '\n') {
                    currentRow.add(currentField.toString());
                    currentField.setLength(0);
                    rows.add(currentRow);
                    currentRow = new ArrayList<String>();
                } else {
                    currentField.append(c);
                }
            }
        }
        if (currentField.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(currentField.toString());
            rows.add(currentRow);
        }
        return rows;
    }
}
```

- [ ] **Step 7: Run unit tests to verify PASS**

Run: `$env:JAVA_HOME="D:\android-studio-ide\jre"; .\gradlew.bat testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, `DateAmountUtilsTest` and `CsvCodecTest` pass.

---

### Task 2: SQLite Database Layer (`AccountBookDbHelper`, `CategoryDao`, `BillDao`)

**Files:**
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\db\AccountBookDbHelper.java`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\db\CategoryDao.java`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\db\BillDao.java`

**Interfaces:**
- Consumes: `Category`, `Bill`, `CategoryStat`, `DateAmountUtils`.
- Produces:
  - `AccountBookDbHelper.getInstance(Context)` singleton managing `account_book.db` (v1), `TABLE_CATEGORY = "category"`, `TABLE_BILL = "bill"`, and `onConfigure(db)` enabling foreign keys.
  - `CategoryDao`: `getByType(int type)`, `getAll()`, `findByNameAndType(String name, int type)`, `findOrCreateCategory(SQLiteDatabase db, String name, int type)`, `addCustomCategory(String name, int type)` (returns new id or `-1` if duplicate), `deleteCustomCategory(long categoryId)` (returns status code: `0` success, `1` is default, `2` in use by bills).
  - `BillDao`: `insert(Bill)`, `update(Bill)`, `delete(long billId)`, `getById(long billId)`, `getRecentBills(int limit)`, `queryBills(String monthPrefix, int type, long categoryId, String keyword)`, `getAllBills()`, `getMonthlyTotal(String monthPrefix, int type)`, `getCategoryStats(String monthPrefix, int type)`, `getDistinctMonths()`, `deleteAllBills()`, `seedDemoBills()`, `insertBillsInTransaction(List<Bill>, CategoryDao)`.

- [ ] **Step 1: Implement `AccountBookDbHelper.java`**

```java
package com.example.walletkeep_demo.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class AccountBookDbHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "account_book.db";
    public static final int DB_VERSION = 1;

    public static final String TABLE_CATEGORY = "category";
    public static final String TABLE_BILL = "bill";

    private static volatile AccountBookDbHelper instance;

    public static AccountBookDbHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (AccountBookDbHelper.class) {
                if (instance == null) {
                    instance = new AccountBookDbHelper(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private AccountBookDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_CATEGORY + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, "
                + "type INTEGER NOT NULL, "
                + "icon TEXT, "
                + "is_default INTEGER DEFAULT 0"
                + ");");

        db.execSQL("CREATE TABLE " + TABLE_BILL + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "amount REAL NOT NULL, "
                + "type INTEGER NOT NULL, "
                + "category_id INTEGER NOT NULL, "
                + "note TEXT, "
                + "bill_date TEXT NOT NULL, "
                + "receipt_path TEXT, "
                + "created_at TEXT NOT NULL, "
                + "FOREIGN KEY(category_id) REFERENCES " + TABLE_CATEGORY + "(id)"
                + ");");

        insertDefaultCategories(db);
    }

    private void insertDefaultCategories(SQLiteDatabase db) {
        String[][] expenseCategories = {
                {"餐饮", "餐"}, {"交通", "交"}, {"购物", "购"}, {"学习", "学"},
                {"娱乐", "娱"}, {"医疗", "医"}, {"居家", "居"}, {"其他支出", "支"}
        };
        for (String[] item : expenseCategories) {
            db.execSQL("INSERT INTO " + TABLE_CATEGORY + " (name, type, icon, is_default) VALUES (?, 0, ?, 1)",
                    new Object[]{item[0], item[1]});
        }
        String[][] incomeCategories = {
                {"工资", "薪"}, {"奖金", "奖"}, {"兼职", "兼"},
                {"理财", "财"}, {"生活费", "费"}, {"其他收入", "收"}
        };
        for (String[] item : incomeCategories) {
            db.execSQL("INSERT INTO " + TABLE_CATEGORY + " (name, type, icon, is_default) VALUES (?, 1, ?, 1)",
                    new Object[]{item[0], item[1]});
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BILL);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CATEGORY);
        onCreate(db);
    }
}
```

- [ ] **Step 2: Implement `CategoryDao.java`**

Implement all methods listed in Interfaces: `getByType(int)`, `getAll()`, `findByNameAndType(String, int)`, `findOrCreateCategory(SQLiteDatabase, String, int)`, `addCustomCategory(String, int)` (auto-deriving 1-character `icon` from first char of `name`), and `deleteCustomCategory(long)` returning `DELETE_OK = 0`, `DELETE_ERR_DEFAULT = 1`, `DELETE_ERR_IN_USE = 2`. Always close `Cursor` in `finally` blocks.

- [ ] **Step 3: Implement `BillDao.java`**

Implement `insert`, `update`, `delete`, `getById`, `getRecentBills(int limit)`, `queryBills(String monthPrefix, int type, long categoryId, String keyword)`, `getAllBills()`, `getMonthlyTotal(String monthPrefix, int type)`, `getCategoryStats(String monthPrefix, int type)` (calculating `percentage` against month total), `getDistinctMonths()`, `deleteAllBills()`, `seedDemoBills()` (inserting 10 realistic expense/income records across the current month and previous month using existing category IDs), and `insertBillsInTransaction(List<Bill>, CategoryDao)` using `beginTransaction()` / `setTransactionSuccessful()` / `endTransaction()`.

- [ ] **Step 4: Compile check**

Run: `$env:JAVA_HOME="D:\android-studio-ide\jre"; .\gradlew.bat compileDebugJavaWithJavac`
Expected: `BUILD SUCCESSFUL`.

---

### Task 3: SharedPreferences, File Storage & Application Entry (`PrefsManager`, `FileStorageManager`, `WalletKeepApp`)

**Files:**
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\storage\PrefsManager.java`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\storage\FileStorageManager.java`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\WalletKeepApp.java`

**Interfaces:**
- Consumes: `Bill`, `BillDao`, `CategoryDao`, `CsvCodec`.
- Produces:
  - `PrefsManager(Context)`:
    - `getMonthlyBudget()`, `setMonthlyBudget(float)`
    - `getDefaultCurrency()`, `setDefaultCurrency(String)`
    - `getThemeMode()`, `setThemeMode(String)` (`"light"` / `"dark"`), `applySavedTheme()`
    - `isReminderEnabled()`, `setReminderEnabled(boolean)`
    - `isFirstLaunch()`, `setFirstLaunch(boolean)`
  - `FileStorageManager`:
    - `exportBillsToCsv(Context, List<Bill>)` -> returns `File` in `files/exports/`
    - `listBackupFiles(Context)` -> returns `List<File>` sorted newest first
    - `readFileText(File)` -> returns `String` (UTF-8)
    - `importBillsFromCsv(Context, File, BillDao, CategoryDao)` -> returns `int` imported count
    - `copyReceiptImage(Context, Uri)` -> returns saved absolute path in `files/receipts/`
    - `decodeSampledBitmap(String path, int reqWidth, int reqHeight)` -> returns `Bitmap` or `null`
    - `deleteReceiptFile(String path)` and `clearAllReceipts(Context)`
  - `WalletKeepApp extends Application` calling `new PrefsManager(this).applySavedTheme()`.

- [ ] **Step 1: Implement `PrefsManager.java`, `FileStorageManager.java`, and `WalletKeepApp.java`**

Write complete, null-safe implementations using `"UTF-8"` streams (closed cleanly in `finally` blocks), `BitmapFactory.Options` sampling to avoid OOM on high-resolution gallery photos, and `AppCompatDelegate.setDefaultNightMode(...)`.

- [ ] **Step 2: Compile check**

Run: `$env:JAVA_HOME="D:\android-studio-ide\jre"; .\gradlew.bat compileDebugJavaWithJavac`
Expected: `BUILD SUCCESSFUL`.

---

### Task 4: Reusable UI Adapters, Custom 2D Chart View & Home Dashboard (`MainActivity`)

**Files:**
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\layout\item_bill.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\layout\item_category_grid.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\layout\item_category_stat.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\ui\adapter\BillListAdapter.java`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\ui\adapter\CategoryGridAdapter.java`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\ui\adapter\CategoryStatAdapter.java`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\ui\view\CategoryChartView.java`
- Modify: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\layout\activity_main.xml`
- Modify: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\MainActivity.java`

**Interfaces:**
- Consumes: `Bill`, `Category`, `CategoryStat`, `BillDao`, `PrefsManager`, `FileStorageManager`, `DateAmountUtils`, and Activity targets (`AddEditBillActivity`, `BillListActivity`, `StatsActivity`, `SettingsActivity`).
- Produces:
  - `BillListAdapter(Context, List<Bill>, String currency)` with `updateData(List<Bill>, String currency)`.
  - `CategoryGridAdapter(Context, List<Category>)` with `setSelectedCategoryId(long)` and `updateData(List<Category>)`.
  - `CategoryStatAdapter(Context, List<CategoryStat>, String currency)` with `updateData(List<CategoryStat>, String currency)`.
  - `CategoryChartView(Context, AttributeSet)` with `setStats(List<CategoryStat>, String currency)` rendering 2D Canvas rounded horizontal bars + percentage & amount labels + empty-state message.
  - `MainActivity` displaying monthly expense/income/balance card, dynamic budget progress bar with 80%/100% warnings, quick nav buttons, recent bills list (click to edit, long-click to delete), FAB "记一笔", and first-launch demo-data dialog.

- [ ] **Step 1: Create item XML layouts (`item_bill.xml`, `item_category_grid.xml`, `item_category_stat.xml`)**
- [ ] **Step 2: Implement `BillListAdapter`, `CategoryGridAdapter`, `CategoryStatAdapter`, and `CategoryChartView`**
- [ ] **Step 3: Implement `activity_main.xml` and `MainActivity.java`**

---

### Task 5: Add/Edit Bill, Receipt Photo Attachment & Custom Category Management (`AddEditBillActivity`)

**Files:**
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\layout\activity_add_edit_bill.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\AddEditBillActivity.java`

**Interfaces:**
- Consumes: `BillDao`, `CategoryDao`, `PrefsManager`, `FileStorageManager`, `CategoryGridAdapter`, `DateAmountUtils`.
- Produces:
  - `AddEditBillActivity.EXTRA_BILL_ID = "extra_bill_id"` (`long`, default `-1`).
  - Complete add/edit/delete bill workflow, expense/income toggle, `GridView` category selection, "+ 新增分类" dialog, long-press custom category deletion with foreign-key guard, `DatePickerDialog`, gallery receipt picker (`ACTION_GET_CONTENT`) saving to `files/receipts/`, thumbnail preview, tap-to-zoom dialog, and remove-receipt button.

- [ ] **Step 1: Create `activity_add_edit_bill.xml`**
- [ ] **Step 2: Implement `AddEditBillActivity.java`**

---

### Task 6: Bill List Multi-Filter/Search & Statistics Page (`BillListActivity`, `StatsActivity`)

**Files:**
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\layout\activity_bill_list.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\BillListActivity.java`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\layout\activity_stats.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\StatsActivity.java`

**Interfaces:**
- Consumes: `BillDao`, `CategoryDao`, `PrefsManager`, `FileStorageManager`, `BillListAdapter`, `CategoryStatAdapter`, `CategoryChartView`, `DateAmountUtils`, `AddEditBillActivity.EXTRA_BILL_ID`.
- Produces:
  - `BillListActivity`: search `EditText` with live filtering (`TextWatcher`) + clear button, month `Spinner` ("全部月份" + distinct months), type `Spinner` ("全部类型" / "仅支出" / "仅收入"), category `Spinner` ("全部分类" + categories), filtered summary bar (count / expense / income), bill `ListView` (tap to edit, long-press to delete).
  - `StatsActivity`: prev/next month controls, expense/income toggle tabs, monthly summary & budget usage card, `CategoryChartView`, and ranked `ListView` of category statistics.

- [ ] **Step 1: Create `activity_bill_list.xml` and `BillListActivity.java`**
- [ ] **Step 2: Create `activity_stats.xml` and `StatsActivity.java`**

---

### Task 7: Settings, CSV Backup/Restore, Manifest Wiring & Full Build Verification

**Files:**
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\res\layout\activity_settings.xml`
- Create: `D:\26term-android-homework\WalletKeep_demo\app\src\main\java\com\example\walletkeep_demo\SettingsActivity.java`
- Modify: `D:\26term-android-homework\WalletKeep_demo\app\src\main\AndroidManifest.xml`

**Interfaces:**
- Consumes: `PrefsManager`, `FileStorageManager`, `BillDao`, `CategoryDao`, `DateAmountUtils`, `WalletKeepApp`, all 5 Activities.
- Produces:
  - `SettingsActivity`: monthly budget input + save, budget alert switch, currency selector (`¥` / `$` / `€`), light/dark theme toggle (recreating activity cleanly), Export CSV button (dialog showing path & bytes), Import CSV button (backup picker dialog + transactional restore), Preview CSV Content button (reads file from `files/exports/` in scrollable dialog), Generate Demo Data button, and Clear All Data button (with confirmation dialog + deleting receipt files).
  - Updated `AndroidManifest.xml` registering `.WalletKeepApp`, `.MainActivity`, `.AddEditBillActivity`, `.BillListActivity`, `.StatsActivity`, `.SettingsActivity` with `parentActivityName` / ActionBar back support.

- [ ] **Step 1: Create `activity_settings.xml` and `SettingsActivity.java`**
- [ ] **Step 2: Register `WalletKeepApp` and all 5 Activities in `AndroidManifest.xml`**
- [ ] **Step 3: Full clean build, unit tests, and APK verification**

Run:
```powershell
$env:JAVA_HOME="D:\android-studio-ide\jre"
cd D:\26term-android-homework\WalletKeep_demo
.\gradlew.bat clean testDebugUnitTest assembleDebug
```
Expected:
- `BUILD SUCCESSFUL`
- All JUnit unit tests pass (`DateAmountUtilsTest`, `CsvCodecTest`, `ExampleUnitTest`)
- APK generated at `D:\26term-android-homework\WalletKeep_demo\app\build\outputs\apk\debug\app-debug.apk`.
