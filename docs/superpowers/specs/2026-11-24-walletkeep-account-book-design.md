# 《记账小助手》(WalletKeep) Android 本地数据持久化应用设计规格说明书

- **项目路径**：`D:\26term-android-homework\WalletKeep_demo`
- **包名 (`applicationId`)**：`com.example.walletkeep_demo`
- **选题方向**：基于 Android 本地数据存储的应用程序（SQLite + 文件存储 + SharedPreferences 简单存储）
- **开发语言与技术栈**：Java + Android XML 传统视图 + 原生 `SQLiteOpenHelper` + 应用私有目录文件读写 + `SharedPreferences`
- **日期**：2026-11-24

---

## 1. 设计背景与目标

本项目围绕 Android 本地数据持久化技术为核心，开发一款完全离线、具有实际日常使用价值的个人收支记账系统——**《记账小助手》(WalletKeep)**。

系统严格对标课程设计任务书要求：
1. **三类本地数据存储技术有机结合**：
   - **SQLite 数据库（核心，2 张关联表）**：存储收支分类（`category`）与账单明细（`bill`），支持完整的增删改查（CRUD）、多条件关联筛选及按月/按分类聚合统计。
   - **文件存储（File Storage，2 类实际场景）**：在应用私有目录（`context.getFilesDir()`）下实现账单 CSV 备份文件的导出与导入恢复（`files/exports/`），以及消费票据/小票图片附件的本地持久化保存（`files/receipts/`）。
   - **简单存储（SharedPreferences）**：保存月度预算金额、预算超支提醒开关、默认币种符号、深浅色主题模式以及首次启动标记（`app_settings.xml`）。
2. **功能饱满（6 大核心功能模块，5 个独立 Activity 页面）**：涵盖首页看板与预算监控、记账录入与票据图片附件、自定义分类管理、账单明细多维筛选与备注搜索、月度收支统计与自定义 2D 图表可视化、CSV 数据备份恢复与系统设置。

---

## 2. 编译环境与工程配置规范

为了与本地 Android Studio (AGP `3.6.3` + Gradle `5.6.4` + SDK `D:\26-term-android-sdk`) 离线缓存完全兼容，工程构建配置统一对齐同目录下已验证通过的实验工程标准：
- **根目录 `build.gradle`**：使用 `google()` 与 `mavenCentral()` 仓库，AGP 版本 `com.android.tools.build:gradle:3.6.3`。
- **`app/build.gradle`**：
  - `compileSdkVersion 29`
  - `buildToolsVersion "28.0.3"`
  - `minSdkVersion 17`
  - `targetSdkVersion 29`
  - 核心依赖：`androidx.appcompat:appcompat:1.0.2`、`androidx.constraintlayout:constraintlayout:1.1.3`、`junit:junit:4.12`。
  - 列表与网格组件统一采用 Android 原生 `ListView` / `GridView` + `BaseAdapter` 及自定义 `View`，零额外第三方网络依赖，确保 100% 离线编译通过。

---

## 3. 系统架构与包结构设计

项目代码位于 `app/src/main/java/com/example/walletkeep_demo/` 下，按职责划分为 5 个子包：

```text
com.example.walletkeep_demo
├── model/
│   ├── Category.java              // 收支分类实体类
│   ├── Bill.java                  // 账单明细实体类（含关联查询出的分类名称与图标名）
│   └── CategoryStat.java          // 分类统计结果实体类（分类名、金额、占比、笔数）
├── db/
│   ├── AccountBookDbHelper.java   // SQLiteOpenHelper：建表、预置默认分类、可选生成演示数据
│   ├── CategoryDao.java           // 分类表 CRUD 与外键引用检查
│   └── BillDao.java               // 账单表 CRUD、多维筛选搜索、月度汇总与分类聚合统计
├── storage/
│   ├── FileStorageManager.java    // 文件存储：CSV 导出/导入、票据图片复制保存与清理
│   └── PrefsManager.java          // SharedPreferences：月度预算、币种、主题、提醒开关、首次启动
├── ui/
│   ├── adapter/
│   │   ├── BillListAdapter.java       // 账单列表适配器
│   │   ├── CategoryGridAdapter.java   // 记账页分类网格选择适配器
│   │   └── CategoryStatAdapter.java   // 统计页分类占比排行适配器
│   └── view/
│       └── CategoryChartView.java     // 自定义 2D Canvas 分类统计柱状/占比图组件
├── util/
│   └── DateAmountUtils.java       // 日期格式化（yyyy-MM-dd / yyyy-MM）与金额格式化工具
├── MainActivity.java              // 1. 首页总览、预算进度监控与最近账单
├── AddEditBillActivity.java       // 2. 新增/编辑/删除账单、票据图片附件、自定义分类管理
├── BillListActivity.java          // 3. 账单明细、按月/类型/分类筛选与备注关键词搜索
├── StatsActivity.java             // 4. 月度收支统计、自定义 2D 图表与分类占比排行榜
└── SettingsActivity.java          // 5. 预算设置、CSV 文件导出/导入恢复、主题/币种/重置
```

---

## 4. 三层本地持久化详细设计

### 4.1 SQLite 数据库设计 (`account_book.db`, Version = 1)

#### 表 1：收支分类表 `category`
```sql
CREATE TABLE category (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    type INTEGER NOT NULL,        -- 0: 支出, 1: 收入
    icon TEXT,                    -- 分类标识/简称（如 "餐", "交", "购"）
    is_default INTEGER DEFAULT 0  -- 1: 系统预置不可删, 0: 用户自定义可删
);
```
- **预置分类初始化**：
  - 支出（`type = 0`）：餐饮、交通、购物、学习、娱乐、医疗、居家、其他支出
  - 收入（`type = 1`）：工资、奖金、兼职、理财、生活费、其他收入

#### 表 2：账单明细表 `bill`
```sql
CREATE TABLE bill (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    amount REAL NOT NULL,         -- 金额（> 0）
    type INTEGER NOT NULL,        -- 0: 支出, 1: 收入
    category_id INTEGER NOT NULL, -- 外键关联 category(id)
    note TEXT,                    -- 备注说明
    bill_date TEXT NOT NULL,      -- 账单日期 yyyy-MM-dd
    receipt_path TEXT,            -- 票据图片本地私有目录路径，可为空
    created_at TEXT NOT NULL,     -- 创建时间 yyyy-MM-dd HH:mm:ss
    FOREIGN KEY(category_id) REFERENCES category(id)
);
```

#### 核心 SQL 查询逻辑
1. **多条件筛选与关键词搜索账单**：
   ```sql
   SELECT b.*, c.name AS category_name, c.icon AS category_icon
   FROM bill b
   LEFT JOIN category c ON b.category_id = c.id
   WHERE (? = '' OR b.bill_date LIKE ? || '%')
     AND (? = -1 OR b.type = ?)
     AND (? = -1 OR b.category_id = ?)
     AND (? = '' OR b.note LIKE '%' || ? || '%' OR c.name LIKE '%' || ? || '%')
   ORDER BY b.bill_date DESC, b.id DESC;
   ```
2. **指定月份总支出 / 总收入聚合**：
   ```sql
   SELECT IFNULL(SUM(amount), 0)
   FROM bill
   WHERE type = ? AND bill_date LIKE ? || '%';
   ```
3. **指定月份按分类聚合统计**：
   ```sql
   SELECT c.id, c.name, c.icon, COUNT(b.id) AS bill_count, IFNULL(SUM(b.amount), 0) AS total_amount
   FROM bill b
   JOIN category c ON b.category_id = c.id
   WHERE b.type = ? AND b.bill_date LIKE ? || '%'
   GROUP BY b.category_id
   ORDER BY total_amount DESC;
   ```

### 4.2 文件存储设计 (`FileStorageManager`)

全部存放在应用私有目录 `context.getFilesDir()` 下，无需申请运行时外部存储权限，兼容所有 Android 版本：

1. **CSV 账单备份导出 (`files/exports/bill_backup_yyyyMMdd_HHmmss.csv`)**
   - 首行表头：`id,amount,type,category,note,bill_date,receipt_path,created_at`
   - 字段转义：对 `category` 与 `note` 中包含英文逗号 `,`、双引号 `"` 或换行符的内容按 RFC 4180 规范用双引号包裹转义。
2. **CSV 账单导入恢复**
   - 扫描 `files/exports/` 目录下所有 `.csv` 文件并按时间倒序展示。
   - 用户选择某个备份文件后，开启 SQLite 事务（`db.beginTransaction()`），逐行解析 CSV：
     - 根据 `(name, type)` 查找 `category` 表；若该分类不存在，自动创建一条自定义分类并获取 `category_id`；
     - 插入 `bill` 表，全部成功后提交事务（`setTransactionSuccessful()`）并提示导入成功条数。
3. **票据/小票图片保存 (`files/receipts/IMG_<timestamp>.jpg`)**
   - 在记账页面点击“添加票据图片”时，通过 `Intent.ACTION_GET_CONTENT` (`image/*`) 选择图片；
   - 通过 `ContentResolver.openInputStream(uri)` 读取字节流，压缩或直接复制到 `new File(context.getFilesDir(), "receipts/IMG_" + System.currentTimeMillis() + ".jpg")`；
   - 将生成的绝对路径存入 `bill.receipt_path` 字段。

### 4.3 SharedPreferences 简单存储设计 (`PrefsManager`)

配置文件名：`app_settings`（`Context.MODE_PRIVATE`）：

| Key 名称 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `monthly_budget` | `float` | `3000.0f` | 月度支出预算上限 |
| `default_currency` | `String` | `"¥"` | 默认币种符号（可选 `¥`, `$`, `€`） |
| `theme_mode` | `String` | `"light"` | 界面主题模式（`"light"` 浅色 / `"dark"` 深色） |
| `reminder_enabled` | `boolean` | `true` | 是否开启月度预算超支预警提示 |
| `first_launch` | `boolean` | `true` | 是否首次启动应用 |

---

## 5. 六大功能模块与页面交互设计

### 模块 1：首页总览与月度预算监控 (`MainActivity`)
- **顶部月度看板卡片**：
  - 显示当前月份（支持点击切换月份或默认当月）、本月总支出、本月总收入、本月结余。
  - **预算进度条与状态提示**：读取 `PrefsManager.getMonthlyBudget()`，计算 `本月支出 / 月度预算` 百分比：
    - `< 80%`：绿色正常状态，显示“剩余预算：¥xxx”；
    - `80% ~ 100%`（且开启提醒）：橙色预警状态，显示“预算即将用尽（已用 xx%）”；
    - `> 100%`（且开启提醒）：红色超支状态，显示“本月已超支：¥xxx！”。
- **四大快捷功能入口按钮**：
  - 「记一笔」→ 跳转 `AddEditBillActivity`
  - 「账单明细」→ 跳转 `BillListActivity`
  - 「收支统计」→ 跳转 `StatsActivity`
  - 「设置与备份」→ 跳转 `SettingsActivity`
- **最近账单列表**：展示最新 15 条账单记录；单击某条记录跳转 `AddEditBillActivity` 编辑，长按弹出确认框删除。
- **首次启动引导**：当 `PrefsManager.isFirstLaunch()` 为 `true` 时，弹窗询问是否一键载入示例账单数据（包含当月多笔餐饮/购物/交通/学习支出与工资/兼职收入），方便直接体验完整统计图表与导出功能。

### 模块 2：记账录入、编辑删除与票据图片附件 (`AddEditBillActivity`)
- **模式区分**：通过 `Intent` 传入 `extra_bill_id`（缺省 `-1` 为新增模式，`> 0` 为编辑模式）。
- **收支类型切换**：顶部「支出」「收入」双选项卡切换，动态加载对应类型的分类网格（`GridView`）。
- **表单输入**：
  - 金额输入框（`inputType="numberDecimal"`）
  - 日期选择按钮（点击弹出 `DatePickerDialog`，默认当天 `yyyy-MM-dd`）
  - 备注输入框（支持填写消费详情）
- **票据图片附件区域**：
  - 「选择票据图片」按钮：调用系统相册选图并拷贝至 `files/receipts/`，在下方 `ImageView` 显示缩略图及文件名；
  - 「查看原图 / 移除图片」按钮：点击缩略图可弹窗放大预览，点击移除可清空当前账单关联的票据图片。
- **保存与删除**：校验金额合法后写入 SQLite；编辑模式下提供红色「删除该账单」按钮。

### 模块 3：自定义收支分类管理（内嵌于 `AddEditBillActivity` 分类区及管理弹窗）
- 在记账页分类网格末尾或标题旁提供「+ 新增分类」与「管理分类」按钮：
  - **新增自定义分类**：输入分类名称（如“奶茶”、“数码”），自动按当前收支类型写入 `category` 表（`is_default = 0`）并立即刷新分类网格。
  - **删除自定义分类**：仅允许删除 `is_default = 0` 的用户自定义分类；若该分类下已有账单记录，提示用户先迁移或删除关联账单，防止外键悬空。

### 模块 4：账单明细查询、多维筛选与关键词搜索 (`BillListActivity`)
- **搜索栏**：输入备注或分类关键词，点击搜索或实时过滤账单列表。
- **三维筛选栏**：
  - 月份选择（全部月份 / 指定 `yyyy-MM`）
  - 收支类型（全部 / 仅支出 / 仅收入）
  - 分类筛选（全部分类 / 指定分类）
- **筛选汇总条**：动态显示当前筛选结果下的“共 N 笔 | 总支出 ¥xxx | 总收入 ¥xxx”。
- **账单列表**：展示每笔账单的分类圆形徽标、分类名、备注、日期、金额（支出红色 `-¥xx`、收入绿色 `+¥xx`）以及“含票据”标记。点击任意项可进入编辑。

### 模块 5：月度收支统计与自定义 2D 图表可视化 (`StatsActivity`)
- **月份与收支类型切换**：支持「上个月 / 下个月」切换，以及「支出构成 / 收入构成」切换。
- **月度汇总卡片**：显示当月总支出、总收入、结余、预算使用率。
- **自定义 2D 统计图 (`CategoryChartView`)**：
  - 使用 Android 2D `Canvas` + `Paint` 绘制水平对比柱状图与彩色分类占比色块条，直观展示前 N 大消费/收入分类的金额比例。
- **分类占比排行榜列表**：按汇总金额降序展示每个分类的名称、笔数、占当月总额的百分比（`xx.x%`）及汇总金额。

### 模块 6：预算设置、CSV 文件备份恢复与系统设置 (`SettingsActivity`)
- **预算与偏好设置（SharedPreferences）**：
  - 月度预算金额输入框 + 保存按钮
  - 预算超支提醒 `Switch` 开关
  - 默认币种切换（人民币 `¥` / 美元 `$` / 欧元 `€`）
  - 主题模式切换（浅色模式 / 深色夜间模式，调用 `AppCompatDelegate.setDefaultNightMode` 即时生效）
- **数据备份与恢复（文件存储）**：
  - 「导出账单为 CSV 文件」：一键将 SQLite 中所有账单导出至 `files/exports/bill_backup_<时间>.csv`，并在页面及弹窗中展示导出文件路径与字节大小；
  - 「从 CSV 备份文件恢复」：列出 `files/exports/` 目录下所有备份文件，点击即可选择导入恢复，或点击「预览 CSV 内容」直接查看备份文件纯文本内容（直观验证文件存储效果！）。
- **演示数据与重置**：
  - 「补充生成示例账单」按钮
  - 「清空所有账单与票据文件」按钮（带二次确认弹窗）

---

## 6. 异常处理与质量保证

1. **表单输入防呆**：金额为空、格式非法或 `<= 0` 时弹出 `Toast` 提示并聚焦输入框；分类名称为空或重名时禁止重复创建。
2. **CSV 解析与事务安全**：导出时对特殊字符（`,`、`"`、`\n`）严格转义；导入时使用状态机解析 CSV 行，并在数据库事务中执行，遇到损坏文件时自动回滚事务，绝不污染现有数据库。
3. **文件与图片容错**：加载 `receipt_path` 图片时使用 `BitmapFactory.Options` 采样缩放（`inSampleSize`），防止大图 OOM；若文件不存在则优雅隐藏图片框。
4. **自动化测试与构建验证**：
   - 编写 JUnit 单元测试验证 CSV 转义/解析与金额日期格式化逻辑；
   - 使用 `./gradlew testDebugUnitTest assembleDebug` 完成完整编译与 APK 打包验证。
