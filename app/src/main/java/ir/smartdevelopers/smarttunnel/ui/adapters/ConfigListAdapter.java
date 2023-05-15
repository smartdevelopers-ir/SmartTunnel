package ir.smartdevelopers.smarttunnel.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Objects;

import ir.smartdevelopers.smarttunnel.databinding.ItemConfigListBinding;
import ir.smartdevelopers.smarttunnel.ui.interfaces.OnListItemClickListener;
import ir.smartdevelopers.smarttunnel.ui.models.ConfigListModel;

public class ConfigListAdapter extends RecyclerView.Adapter<ConfigListAdapter.ConfigViewHolder> {

    private List<ConfigListModel> mModels;
    private OnListItemClickListener<ConfigListModel> mOnDeleteClickListener;
    private OnListItemClickListener<ConfigListModel> mOnEditClickListener;
    private OnListItemClickListener<ConfigListModel> mOnLongClickListener;
    private OnConfigChangeListener mOnConfigChangeListener;
    private boolean disabled;

    public ConfigListAdapter(List<ConfigListModel> models) {
        mModels = models;
    }

    @NonNull
    @Override
    public ConfigViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemConfigListBinding binding = ItemConfigListBinding.inflate(LayoutInflater.from(parent.getContext()),
                parent,false);
        return new ConfigViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ConfigViewHolder holder, int position) {
        holder.bindView(mModels.get(position));
    }

    @Override
    public int getItemCount() {
        return mModels.size();
    }

    public void setOnDeleteClickListener(OnListItemClickListener<ConfigListModel> onDeleteClickListener) {
        mOnDeleteClickListener = onDeleteClickListener;
    }

    public void setOnEditClickListener(OnListItemClickListener<ConfigListModel> onEditClickListener) {
        mOnEditClickListener = onEditClickListener;
    }


    public void setOnConfigChangeListener(OnConfigChangeListener onConfigChangeListener) {
        mOnConfigChangeListener = onConfigChangeListener;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
        notifyDataSetChanged();
    }

    public void itemDeleted(int position) {
        mModels.remove(position);
        notifyItemRemoved(position);
    }

    public void addConfig(ConfigListModel config, int pos) {
        mModels.add(pos,config);
        notifyItemInserted(pos);
    }

    public void addConfig(ConfigListModel listModel) {
        addConfig(listModel,mModels.size());
    }

    public void configUpdated(ConfigListModel config) {
        int pos = mModels.indexOf(config);
        if (pos != -1){
            if (mModels.get(pos).isSelected()){
                config.setSelected(true);
            }
            mModels.set(pos,config);
            notifyItemChanged(pos);
        }
    }

    public ConfigListAdapter setOnLongClickListener(OnListItemClickListener<ConfigListModel> onLongClickListener) {
        mOnLongClickListener = onLongClickListener;
        return this;
    }

    class ConfigViewHolder extends RecyclerView.ViewHolder {
        ItemConfigListBinding mBinding;
        public ConfigViewHolder(@NonNull ItemConfigListBinding binding) {
            super(binding.getRoot());
            mBinding=binding;
            mBinding.btnDelete.setOnClickListener(v->{
                if (mOnDeleteClickListener!=null){
                    mOnDeleteClickListener.onItemClicked(itemView,mModels.get(getAdapterPosition()),getAdapterPosition());
                }
            });
            mBinding.btnEdit.setOnClickListener(v->{
                if (mOnEditClickListener!=null){
                    mOnEditClickListener.onItemClicked(itemView,mModels.get(getAdapterPosition()),getAdapterPosition());
                }
            });
            mBinding.getRoot().setOnClickListener(v->{
                if (!disabled){
                    setSelected(mModels.get(getAdapterPosition()));
                }
            });
            mBinding.getRoot().setOnLongClickListener(v->{
                if (!disabled){
                    if (mOnLongClickListener != null) {
                        mOnLongClickListener.onItemClicked(itemView,mModels.get(getAdapterPosition()),getAdapterPosition());
                        return true;
                    }
                }
                return false;
            });
        }
        void bindView(ConfigListModel model){
            mBinding.txtConfigName.setText(model.name);
            if (model.isSelected()){
                mBinding.activeSign.setVisibility(View.VISIBLE);
            }else {
                mBinding.activeSign.setVisibility(View.INVISIBLE);
            }
            mBinding.txtConfigType.setText(model.type);
            mBinding.getRoot().setEnabled(!disabled);
        }
    }

    private void setSelected(ConfigListModel selected) {
        for (int i = 0;i<mModels.size();i++){
            ConfigListModel model = mModels.get(i);
            if (model.isSelected()){
                if (Objects.equals(selected,model)){
                    return;
                }
                model.setSelected(false);

                notifyItemChanged(i);

                break;
            }
        }
        selected.setSelected(true);
        int selectedPos = mModels.indexOf(selected);
        if (selectedPos >= 0){
            notifyItemChanged(selectedPos);
        }
        if (mOnConfigChangeListener!=null){
            mOnConfigChangeListener.onConfigChanged(selected);
        }
    }

    public interface OnConfigChangeListener{
        void onConfigChanged(ConfigListModel model);
    }
}
