package com.example.olhovivosp.ui;
import android.view.ViewGroup;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;
import java.util.function.Consumer;

/** Paginação local: a API SPTrans retorna o resultado completo, sem offset/limit. */
public final class PageController<T> {
    public static final int PAGE_SIZE = 20;
    private List<T> items = Collections.emptyList();
    private int page;
    private final Consumer<List<T>> submit;
    private final LinearLayout bar;
    private final Button previous, next;
    private final TextView label;
    private final RecyclerView recycler;
    public PageController(RecyclerView recycler, Consumer<List<T>> submit) {
        this.recycler = recycler; this.submit = submit;
        android.content.Context context = recycler.getContext();
        bar = new LinearLayout(context); bar.setGravity(android.view.Gravity.CENTER);
        previous = new Button(context); previous.setText("Anterior");
        next = new Button(context); next.setText("Próxima");
        label = new TextView(context); label.setPadding(16, 0, 16, 0);
        bar.addView(previous); bar.addView(label); bar.addView(next);
        ViewGroup parent = (ViewGroup) recycler.getParent();
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
        int start = page * PAGE_SIZE;
        submit.accept(new ArrayList<>(items.subList(start, Math.min(start + PAGE_SIZE, items.size()))));
        previous.setEnabled(page > 0); next.setEnabled(start + PAGE_SIZE < items.size());
        label.setText((page + 1) + " / " + Math.max(1, (items.size() + 19) / 20));
        bar.setVisibility(items.size() > PAGE_SIZE ? android.view.View.VISIBLE : android.view.View.GONE);
        recycler.scrollToPosition(0);
    }
}
