package com.janioofi.nopontosp.ui;
import android.view.ViewGroup;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import java.util.*;
import java.util.function.Consumer;

/** Paginação local: a API SPTrans retorna o resultado completo, sem offset/limit. */
public final class PageController<T> {
    public static final int PAGE_SIZE = 20;
    private List<T> items = Collections.emptyList();
    private int page;
    private final Consumer<List<T>> submit;
    private final LinearLayout bar;
    private final MaterialButton previous, next;
    private final TextView label;
    private final RecyclerView recycler;
    private final int pageSize;
    public PageController(RecyclerView recycler, Consumer<List<T>> submit) {
        this(recycler, submit, PAGE_SIZE);
    }
    public PageController(RecyclerView recycler, Consumer<List<T>> submit, int pageSize) {
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be positive");
        this.recycler = recycler; this.submit = submit;
        this.pageSize = pageSize;
        android.content.Context context = recycler.getContext();
        bar = new LinearLayout(context); bar.setGravity(Gravity.CENTER);
        int primary = androidx.core.content.ContextCompat.getColor(context, com.janioofi.nopontosp.R.color.primary_teal);
        int soft = androidx.core.content.ContextCompat.getColor(context, com.janioofi.nopontosp.R.color.primary_soft);
        previous = createButton(context, "Anterior", primary, soft);
        next = createButton(context, "Próxima", primary, soft);
        label = new TextView(context);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        label.setTextColor(androidx.core.content.ContextCompat.getColor(context, com.janioofi.nopontosp.R.color.text_primary));
        label.setTypeface(null, Typeface.BOLD);
        bar.addView(previous, new LinearLayout.LayoutParams(0, dp(context, 44), 1f));
        bar.addView(label, new LinearLayout.LayoutParams(-2, dp(context, 44)));
        bar.addView(next, new LinearLayout.LayoutParams(0, dp(context, 44), 1f));
        ViewGroup parent = (ViewGroup) recycler.getParent();
        bar.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 8));
        parent.addView(bar, parent.indexOfChild(recycler) + 1,
            new ViewGroup.LayoutParams(-1, -2));
        previous.setOnClickListener(v -> { page--; render(); });
        next.setOnClickListener(v -> { page++; render(); });
        render();
    }
    public void setItems(List<T> values) {
        items = values == null ? Collections.emptyList() : new ArrayList<>(values);
        page = 0; render();
    }
    private void render() {
        int start = page * pageSize;
        submit.accept(new ArrayList<>(items.subList(start, Math.min(start + pageSize, items.size()))));
        previous.setEnabled(page > 0); next.setEnabled(start + pageSize < items.size());
        label.setText((page + 1) + " de " + Math.max(1, (items.size() + pageSize - 1) / pageSize));
        bar.setVisibility(items.size() > pageSize ? android.view.View.VISIBLE : android.view.View.GONE);
        recycler.scrollToPosition(0);
    }

    private static MaterialButton createButton(android.content.Context context, String text,
                                               int foreground, int background) {
        MaterialButton button = new MaterialButton(context);
        button.setText(text);
        button.setTextColor(foreground);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setCornerRadius(dp(context, 14));
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        return button;
    }

    private static int dp(android.content.Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
