package com.example.walletkeep_demo.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.walletkeep_demo.R;
import com.example.walletkeep_demo.model.CategoryStat;
import com.example.walletkeep_demo.util.DateAmountUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 分类统计明细列表适配器（支持 0.1% 精度进度条）。
 */
public class CategoryStatAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<CategoryStat> items;
    private String currency;

    public CategoryStatAdapter(Context context, List<CategoryStat> items, String currency) {
        this.inflater = LayoutInflater.from(context);
        this.items = new ArrayList<>();
        if (items != null) {
            this.items.addAll(items);
        }
        this.currency = normalizeCurrency(currency);
    }

    public void updateData(List<CategoryStat> newItems, String newCurrency) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        this.currency = normalizeCurrency(newCurrency);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public CategoryStat getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getCategoryId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_category_stat, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        CategoryStat stat = items.get(position);
        String name = stat.getCategoryName() != null ? stat.getCategoryName().trim() : "";
        String displayName = name.isEmpty() ? "未分类" : name;
        String icon = stat.getCategoryIcon() != null ? stat.getCategoryIcon().trim() : "";
        if (icon.isEmpty()) {
            icon = !name.isEmpty() ? name.substring(0, 1) : "类";
        }

        double pct = Math.max(0.0, Math.min(100.0, stat.getPercentage()));
        int progress = (int) Math.round(pct * 10.0);

        holder.tvIcon.setText(icon);
        holder.tvName.setText(displayName);
        holder.tvCount.setText(String.format(Locale.CHINA, " · %d笔", stat.getBillCount()));
        holder.tvPercent.setText(String.format(Locale.CHINA, "%.1f%%", pct));
        holder.pbRatio.setProgress(progress);
        holder.tvAmount.setText(DateAmountUtils.formatAmount(currency, stat.getTotalAmount()));

        return convertView;
    }

    private static String normalizeCurrency(String raw) {
        return (raw == null || raw.trim().isEmpty()) ? "¥" : raw.trim();
    }

    private static final class ViewHolder {
        final TextView tvIcon;
        final TextView tvName;
        final TextView tvCount;
        final TextView tvPercent;
        final ProgressBar pbRatio;
        final TextView tvAmount;

        ViewHolder(View root) {
            this.tvIcon = root.findViewById(R.id.tv_stat_icon);
            this.tvName = root.findViewById(R.id.tv_stat_name);
            this.tvCount = root.findViewById(R.id.tv_stat_count);
            this.tvPercent = root.findViewById(R.id.tv_stat_percent);
            this.pbRatio = root.findViewById(R.id.pb_stat_ratio);
            this.tvAmount = root.findViewById(R.id.tv_stat_amount);
        }
    }
}
