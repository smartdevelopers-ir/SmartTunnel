package ir.smartdevelopers.smarttunnel.ui.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.gson.Gson;

import ir.smartdevelopers.smarttunnel.databinding.DialogConfigMoreBottomSheetBinding;
import ir.smartdevelopers.smarttunnel.ui.models.ConfigListModel;

public class ConfigMoreBottomSheet extends BottomSheetDialogFragment {
    private DialogConfigMoreBottomSheetBinding mBinding;
    private OnConvertClickListener mOnConvertClickListener;
    private ConfigListModel mConfigListModel;

    public static ConfigMoreBottomSheet getInstance(ConfigListModel configListModel) {
        Bundle bundle = new Bundle();
        bundle.putString("config",new Gson().toJson(configListModel));
        ConfigMoreBottomSheet dialog = new ConfigMoreBottomSheet();
        dialog.setArguments(bundle);
        return dialog;
    }
    public void showDialog(FragmentManager fragmentManager){
        if (isAdded()){
            return;
        }
        String tag = "config_dialog";
        if (fragmentManager.findFragmentByTag(tag) == null){
            show(fragmentManager,tag);
        }
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (getParentFragment() instanceof OnConvertClickListener){
            mOnConvertClickListener = (OnConvertClickListener) getParentFragment();
        } else if (getActivity() instanceof OnConvertClickListener) {
            mOnConvertClickListener = (OnConvertClickListener) getActivity();
        }
        if (getArguments() != null){
            String configJson = getArguments().getString("config");
            if (!TextUtils.isEmpty(configJson)){
                mConfigListModel = new Gson().fromJson(configJson,ConfigListModel.class);
            }
        }
        mBinding = DialogConfigMoreBottomSheetBinding.inflate(inflater,container,false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBinding.txtConvertToSsh.setOnClickListener(v->{
            if (mOnConvertClickListener != null) {
                dismiss();
                mOnConvertClickListener.onConvertClicked(mConfigListModel);

            }
        });
    }

    public interface OnConvertClickListener{
        void onConvertClicked(ConfigListModel model);
    }
}
