package com.example.walletkeep_demo.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.walletkeep_demo.R;
import com.example.walletkeep_demo.model.Category;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类网格选择适配器（支持选中高亮与自定义标记）。
 */
public class CategoryGridAdapter extends BaseAdapter {

    private final Context context;
    private final LayoutInflater inflater;
    private final List<Category> items;
    private long selectedCategoryId = -1L;

    public CategoryGridAdapter(Context context, List<Category> items) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.items = new ArrayList<>();
        if (items != null) {
            this.items.addAll(items);
        }
    }

    public void setSelectedCategoryId(long categoryId) {
        this.selectedCategoryId = categoryId;
        notifyDataSetChanged();
    }

    public long getSelectedCategoryId() {
        return selectedCategoryId;
    }

    public void updateData(List<Category> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Category getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_category_grid, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Category category = items.get(position);
        String name = category.getName() != null ? category.getName().trim() : "";
        String icon = category.getIcon() != null ? category.getIcon().trim() : "";
        if (icon.isEmpty()) {
            icon = !name.isEmpty() ? name.substring(0, 1) : "类";
        }

        holder.tvIcon.setText(icon);
        holder.tvName.setText(name);
        holder.tvCustomBadge.setVisibility(category.isDefault() ? View.GONE : View.VISIBLE);

        boolean selected = category.getId() == selectedCategoryId;
        if (selected) {
            holder.container.setBackgroundResource(R.drawable.bg_tab_selected);
            int onPrimary = ContextCompat.getColor(context, R.color.textOnPrimary);
            holder.tvName.setTextColor(onPrimary);
            holder.tvCustomBadge.setTextColor(onPrimary);
        } else {
            holder.container.setBackgroundResource(R.drawable.bg_card);
            holder.tvName.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
            holder.tvCustomBadge.setTextColor(ContextCompat.getColor(context, R.color.textSecondary));
        }

        return convertView;
    }

    private static final class ViewHolder {
        final View container;
        final TextView tvIcon;
        final TextView tvName;
        final TextView tvCustomBadge;

        ViewHolder(View root) {
            this.container = root.findViewById(R.id.layout_category_item);
            this.tvIcon = root.findViewById(R.id.tv_cat_icon);
            this.tvName = root.findViewById(R.id.tv_cat_name);
            this.tvCustomBadge = root.findViewById(R.id.tv_cat_custom_badge);
        }
    }
}
