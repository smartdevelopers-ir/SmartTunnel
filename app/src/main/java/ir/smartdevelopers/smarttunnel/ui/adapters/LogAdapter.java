package ir.smartdevelopers.smarttunnel.ui.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import ir.smartdevelopers.smarttunnel.databinding.ItemLogBinding;
import ir.smartdevelopers.smarttunnel.ui.models.LogItem;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {
    private List<LogItem> mLogItems;

    public LogAdapter(List<LogItem> logItems) {
        mLogItems = logItems;
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLogBinding binding = ItemLogBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false);
        return new LogViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        holder.bindView(mLogItems.get(position));
    }

    @Override
    public int getItemCount() {
        return mLogItems.size();
    }
    public void addLog(LogItem logItem){
        mLogItems.add(logItem);
        notifyItemInserted(mLogItems.size()-1);
        if (mLogItems.size() > LogItem.MAX_LOG_CACHE_SIZE){
            mLogItems.remove(0);
            notifyItemRemoved(0);
        }

    }

    class LogViewHolder extends RecyclerView.ViewHolder {
        ItemLogBinding mBinding;
        public LogViewHolder(@NonNull ItemLogBinding binding) {
            super(binding.getRoot());
            mBinding = binding;
        }
        void bindView(LogItem item){
            CharSequence logText = HtmlCompat.fromHtml(item.logRawText,HtmlCompat.FROM_HTML_MODE_COMPACT);
            String time = String.format("[%tH:%tM:%tS]  ",item.timeStamp,item.timeStamp,item.timeStamp);
            CharSequence text = TextUtils.concat(time,logText);
            mBinding.txtLog.setText(text, TextView.BufferType.SPANNABLE);

        }
    }
}
