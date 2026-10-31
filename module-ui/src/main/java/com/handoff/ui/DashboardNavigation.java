package com.handoff.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.appcompat.widget.PopupMenu;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;

/** Original View implementation of the two navigation layouts, not upstream Compose code. */
final class DashboardNavigation extends LinearLayout {
    interface OnItemSelectedListener { boolean onItemSelected(MenuItem item); }
    private final Menu menu;
    private final UiStyle style;
    private final int primary, onPrimary, muted;
    private final LinearLayout[] items = new LinearLayout[4];
    private final MaterialTextView[] labels = new MaterialTextView[4];
    private final ImageView[] icons = new ImageView[4];
    private int selected;
    private boolean verticalFallback;
    private OnItemSelectedListener listener;

    DashboardNavigation(Context context, UiStyle style) {
        super(context); this.style = style;
        primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
        onPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary);
        muted = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL); setMinimumHeight(dp(64));
        setPadding(dp(style == UiStyle.MATERIAL ? 8 : 0), dp(8), dp(style == UiStyle.MATERIAL ? 8 : 0), dp(8));
        setSaveEnabled(false);
        menu = new PopupMenu(context, this).getMenu();
        new android.view.MenuInflater(context).inflate(R.menu.dashboard_navigation, menu);
        for (int i = 0; i < 4; i++) {
            final int index = i; MenuItem entry = menu.getItem(i); entry.setCheckable(true);
            LinearLayout item = new LinearLayout(context) {
                @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.Button");
                    info.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(0, 1, index, 1, false, isSelected()));
                }
            };
            item.setId(entry.getItemId()); item.setGravity(Gravity.CENTER); item.setFocusable(true);
            item.setContentDescription(entry.getTitle()); item.setMinimumWidth(dp(48)); item.setMinimumHeight(dp(48));
            ImageView icon = new ImageView(context); icon.setImageDrawable(entry.getIcon());
            icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            MaterialTextView label = new MaterialTextView(context); label.setText(entry.getTitle());
            label.setTextSize(12); label.setIncludeFontPadding(false); label.setGravity(Gravity.CENTER);
            label.setTypeface(Typeface.DEFAULT, Typeface.BOLD); label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(icon, new LayoutParams(dp(24), dp(24))); item.addView(label, new LayoutParams(-2, -2));
            item.setOnClickListener(view -> setSelectedItemId(entry.getItemId()));
            items[i] = item; icons[i] = icon; labels[i] = label;
            addView(item, new LayoutParams(0, -2, 1));
        }
        selected = menu.getItem(0).getItemId(); updateItems();
    }
    Menu getMenu() { return menu; }
    int getSelectedItemId() { return selected; }
    void setOnItemSelectedListener(OnItemSelectedListener listener) { this.listener = listener; }
    void setSelectedItemId(int id) {
        if (id == selected) return;
        MenuItem entry = menu.findItem(id); if (entry == null) return;
        int previous = selected; selected = id; updateItems(); requestLayout();
        if (listener != null && !listener.onItemSelected(entry)) { selected = previous; updateItems(); requestLayout(); }
    }
    private void updateItems() {
        boolean horizontal = style == UiStyle.MATERIAL && !verticalFallback;
        for (int i = 0; i < items.length; i++) {
            boolean checked = menu.getItem(i).getItemId() == selected;
            menu.getItem(i).setChecked(checked); items[i].setSelected(checked);
            items[i].setOrientation(horizontal ? HORIZONTAL : VERTICAL);
            items[i].setPadding(dp(horizontal ? 12 : 2), dp(horizontal ? 8 : 2), dp(horizontal ? 12 : 2), dp(horizontal ? 8 : 2));
            labels[i].setVisibility(horizontal && !checked ? GONE : VISIBLE);
            labels[i].setSingleLine(horizontal); if (!horizontal) labels[i].setMaxLines(2);
            LayoutParams lp = (LayoutParams) labels[i].getLayoutParams();
            lp.setMarginStart(dp(horizontal && checked ? 6 : 0)); lp.topMargin = dp(horizontal ? 0 : 3); labels[i].setLayoutParams(lp);
            int color = checked ? style == UiStyle.MATERIAL ? onPrimary : primary : muted;
            labels[i].setTextColor(color); icons[i].setImageTintList(ColorStateList.valueOf(color));
            GradientDrawable shape = new GradientDrawable(); shape.setCornerRadius(dp(style == UiStyle.MATERIAL ? 28 : 12));
            shape.setColor(checked && style == UiStyle.MATERIAL ? primary : 0);
            GradientDrawable mask = new GradientDrawable(); mask.setColor(0xFFFFFFFF); mask.setCornerRadius(dp(28));
            items[i].setBackground(new RippleDrawable(ColorStateList.valueOf((color & 0xFFFFFF) | 0x22000000), shape, mask));
        }
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int available = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        MaterialTextView selectedLabel = labels[0];
        for (int i = 0; i < 4; i++) if (items[i].getId() == selected) selectedLabel = labels[i];
        int desired = Math.max(dp(96), (int) Math.ceil(selectedLabel.getPaint().measureText(selectedLabel.getText().toString())) + dp(54));
        boolean fallback = style == UiStyle.MATERIAL && available < desired + dp(48)*3;
        if (fallback != verticalFallback) { verticalFallback = fallback; updateItems(); }
        if (style == UiStyle.MATERIAL && !fallback) {
            int iconWidth = Math.max(dp(48), (available - desired) / 3);
            for (LinearLayout item : items) {
                LayoutParams p = (LayoutParams) item.getLayoutParams();
                p.width = item.getId() == selected ? available - 3*iconWidth : iconWidth; p.weight = 0;
            }
        } else for (LinearLayout item : items) {
            LayoutParams p = (LayoutParams) item.getLayoutParams(); p.width = 0; p.weight = 1;
        }
        super.onMeasure(widthSpec, heightSpec);
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain(1, 4, false, AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE));
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
