package com.example.walletkeep_demo.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.walletkeep_demo.R;
import com.example.walletkeep_demo.model.Bill;
import com.example.walletkeep_demo.model.Category;
import com.example.walletkeep_demo.util.DateAmountUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 账单明细列表适配器（ViewHolder 复用模式）。
 */
public class BillListAdapter extends BaseAdapter {

    private final Context context;
    private final LayoutInflater inflater;
    private final List<Bill> items;
    private String currency;

    public BillListAdapter(Context context, List<Bill> items, String currency) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.items = new ArrayList<>();
        if (items != null) {
            this.items.addAll(items);
        }
        this.currency = normalizeCurrency(currency);
    }

    public void updateData(List<Bill> newItems, String newCurrency) {
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
    public Bill getItem(int position) {
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
            convertView = inflater.inflate(R.layout.item_bill, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Bill bill = items.get(position);
        String categoryName = trimToNull(bill.getCategoryName());
        holder.tvCategory.setText(categoryName != null ? categoryName : "未分类");
        holder.tvIcon.setText(resolveIcon(bill.getCategoryIcon(), categoryName));

        String receiptPath = trimToNull(bill.getReceiptPath());
        holder.tvReceiptTag.setVisibility(receiptPath != null ? View.VISIBLE : View.GONE);

        String note = trimToNull(bill.getNote());
        holder.tvNote.setText(note != null ? note : "无备注");

        holder.tvAmount.setText(DateAmountUtils.formatSignedAmount(currency, bill.getType(), bill.getAmount()));
        int amountColorRes = (bill.getType() == Category.TYPE_INCOME)
                ? R.color.colorIncome
                : R.color.colorExpense;
        holder.tvAmount.setTextColor(ContextCompat.getColor(context, amountColorRes));

        String date = trimToNull(bill.getBillDate());
        holder.tvDate.setText(date != null ? date : "");

        return convertView;
    }

    private static String normalizeCurrency(String raw) {
        return (raw == null || raw.trim().isEmpty()) ? "¥" : raw.trim();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String resolveIcon(String rawIcon, String categoryName) {
        String icon = trimToNull(rawIcon);
        if (icon != null) {
            return icon;
        }
        if (categoryName != null && !categoryName.isEmpty()) {
            return categoryName.substring(0, 1);
        }
        return "账";
    }

    private static final class ViewHolder {
        final TextView tvIcon;
        final TextView tvCategory;
        final TextView tvReceiptTag;
        final TextView tvNote;
        final TextView tvAmount;
        final TextView tvDate;

        ViewHolder(View root) {
            this.tvIcon = root.findViewById(R.id.tv_item_icon);
            this.tvCategory = root.findViewById(R.id.tv_item_category);
            this.tvReceiptTag = root.findViewById(R.id.tv_item_receipt_tag);
            this.tvNote = root.findViewById(R.id.tv_item_note);
            this.tvAmount = root.findViewById(R.id.tv_item_amount);
            this.tvDate = root.findViewById(R.id.tv_item_date);
        }
    }
}
