package ir.smartdevelopers.smarttunnel.ui.classes;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class LogLayoutManager extends LinearLayoutManager {
    public LogLayoutManager(Context context) {
        super(context);
    }

    @Override
    public void onItemsAdded(@NonNull RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsAdded(recyclerView, positionStart, itemCount);
        recyclerView.scrollToPosition(recyclerView.getAdapter().getItemCount()-1);
    }
}
