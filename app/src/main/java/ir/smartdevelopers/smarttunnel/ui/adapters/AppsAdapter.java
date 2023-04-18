package ir.smartdevelopers.smarttunnel.ui.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ir.smartdevelopers.smarttunnel.databinding.ItemAppsBinding;
import ir.smartdevelopers.smarttunnel.ui.models.AppModel;
import ir.smartdevelopers.smarttunnel.ui.utils.Util;

public class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.AppsViewHolder> implements Filterable {

    private List<AppModel> mAppModels;
    private List<AppModel> mAppModelCopy;
    private OnSelectAppChangeListener mOnSelectAppChangeListener;

    public AppsAdapter() {
        mAppModels = new ArrayList<>();
        mAppModelCopy = mAppModels;
    }
    public void setList(List<AppModel> models){
        Collections.sort(models, new Comparator<AppModel>() {
            @Override
            public int compare(AppModel o1, AppModel o2) {
                return Boolean.compare(o2.isSelected(),o1.isSelected());
            }
        });
        mAppModelCopy = models;
        mAppModels = models;
        notifyDataSetChanged();
    }

    public List<AppModel> getModelsList() {
        return mAppModelCopy;
    }

    @NonNull
    @Override
    public AppsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAppsBinding binding = ItemAppsBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false);
        return new AppsViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AppsViewHolder holder, int position) {
        holder.bindView(mAppModels.get(position));
    }

    @Override
    public int getItemCount() {
        return mAppModels.size();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                List<AppModel> filtered = new ArrayList<>();
                if (TextUtils.isEmpty(constraint)){
                    filtered = mAppModelCopy;
                }else {
                    for (AppModel model : mAppModelCopy){
                        if (Util.contains(model.getAppName(),constraint) ||
                        Util.contains(model.getPackageName(),constraint)){
                            filtered.add(model);
                        }
                    }
                }
                FilterResults results = new FilterResults();
                results.count = filtered.size();
                results.values = filtered;
                return results;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                mAppModels= (List<AppModel>) results.values;
                notifyDataSetChanged();
            }
        };
    }

    public void setOnSelectAppChangeListener(OnSelectAppChangeListener onSelectAppChangeListener) {
        mOnSelectAppChangeListener = onSelectAppChangeListener;
    }

    class AppsViewHolder extends RecyclerView.ViewHolder {
        ItemAppsBinding mBinding;
        public AppsViewHolder(@NonNull ItemAppsBinding binding) {
            super(binding.getRoot());
            mBinding = binding;
        }
        void bindView(AppModel model){
            mBinding.txtAppName.setText(model.getAppName());
            mBinding.txtAppPackageName.setText(model.getPackageName());
            mBinding.chbCheckbox.setChecked(model.isSelected());
            mBinding.chbCheckbox.jumpDrawablesToCurrentState();
            mBinding.getRoot().setEnabled(model.isEnabled());
            mBinding.getRoot().setOnClickListener(v->{
                mBinding.chbCheckbox.setChecked(!model.isSelected());
                model.setSelected(!model.isSelected());
                if (mOnSelectAppChangeListener != null) {
                    mOnSelectAppChangeListener.onAppSelectionChanged(getSelectedApps());
                }
            });
            mBinding.imgAppIcon.setImageDrawable(model.getIcon());
        }
    }
    public List<AppModel > getSelectedApps(){
        List<AppModel> selectedApps = new ArrayList<>();
        for (AppModel model : mAppModelCopy){
            if (model.isSelected()){
                selectedApps.add(model);
            }
        }
        return selectedApps;
    }
    public interface OnSelectAppChangeListener{
        void onAppSelectionChanged(List<AppModel> selected);
    }
}
