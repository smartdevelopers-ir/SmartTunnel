package ir.smartdevelopers.smarttunnel.ui.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.databinding.FragmentConfigsBinding;
import ir.smartdevelopers.smarttunnel.ui.activities.AddOpenVpnConfigActivity;
import ir.smartdevelopers.smarttunnel.ui.activities.AddSSHConfigActivity;
import ir.smartdevelopers.smarttunnel.ui.adapters.ConfigListAdapter;
import ir.smartdevelopers.smarttunnel.ui.exceptions.ConfigNotSupportException;
import ir.smartdevelopers.smarttunnel.ui.interfaces.OnCompleteListener;
import ir.smartdevelopers.smarttunnel.ui.interfaces.OnListItemClickListener;
import ir.smartdevelopers.smarttunnel.ui.models.Config;
import ir.smartdevelopers.smarttunnel.ui.models.ConfigListModel;
import ir.smartdevelopers.smarttunnel.ui.models.OpenVpnConfig;
import ir.smartdevelopers.smarttunnel.ui.models.SSHConfig;
import ir.smartdevelopers.smarttunnel.ui.services.DeleteConfigFileService;
import ir.smartdevelopers.smarttunnel.ui.utils.AlertUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.ConfigsUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.Util;

public class ConfigsFragment extends Fragment {

    public static final String KEY_CONFIG_URI ="config_uri";
    private FragmentConfigsBinding mBinding;
    private ConfigListAdapter mAdapter;
    private List<ConfigListModel> mConfigListModels;
    private OnListItemClickListener<ConfigListModel> mOnDeleteClickListener;
    private OnListItemClickListener<ConfigListModel> mOnEditClickListener;
    private ConfigListAdapter.OnConfigChangeListener mOnConfigChangeListener;
    private Snackbar undoSnackBar;
    private ConfigListModel mLastDeletedConfig;
    private int mLastDeletedConfigPosition;
    private ActivityResultLauncher<Intent> mAddConfigLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null){
                        Gson gson = new Gson();
                        String configModelJson = result.getData().getStringExtra(AddSSHConfigActivity.KEY_CONFIG_MODEL);
                        ConfigListModel listModel = gson.fromJson(configModelJson,ConfigListModel.class);
                        int mode = result.getData().getIntExtra(AddSSHConfigActivity.KEY_MODE,AddSSHConfigActivity.MODE_ADD);

                        if (mode == AddSSHConfigActivity.MODE_ADD){
                            if (mAdapter.getItemCount() == 0){
                                listModel.setSelected(true);
                                PrefsUtil.setSelectedConfig(requireContext(),listModel);
                            }
                            mAdapter.addConfig(listModel);
                        }else {
                            mAdapter.configUpdated(listModel);
                        }
                        if (getContext() != null){
                            PrefsUtil.addConfig(requireContext(),listModel);
                        }
                        mBinding.txtNoConfigMessage.setVisibility(View.GONE);
                    }
                }
            }
    );

    private ActivityResultLauncher<String> importActivityLuncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
            new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri result) {
                    processImport(result);
                }
            });



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentConfigsBinding.inflate(inflater,container,false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mConfigListModels = PrefsUtil.getAllConfigs(requireContext());
        initViews();
        initListeners();
        mAdapter = new ConfigListAdapter(mConfigListModels);
        mAdapter.setOnConfigChangeListener(mOnConfigChangeListener);
        mAdapter.setOnDeleteClickListener(mOnDeleteClickListener);
        mAdapter.setOnEditClickListener(mOnEditClickListener);
        mBinding.configsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mBinding.configsRecyclerView.addItemDecoration(new DividerItemDecoration(requireContext(),DividerItemDecoration.VERTICAL));
        mBinding.configsRecyclerView.setAdapter(mAdapter);
        if (getArguments() != null) {
            Uri configUri = getArguments().getParcelable(KEY_CONFIG_URI);
            processImport(configUri);
        }
    }

    private void initListeners() {
        mOnDeleteClickListener=new OnListItemClickListener<ConfigListModel>() {
            @Override
            public void onItemClicked(View view, ConfigListModel model, int position) {
                mAdapter.itemDeleted(position);
                PrefsUtil.deleteConfig(requireContext(),model);
                mLastDeletedConfig = model;
                mLastDeletedConfigPosition = position;
                showUndoDeleteAction();
                DeleteConfigFileService.scheduleWork(requireActivity(),8);
                if (mAdapter.getItemCount() == 0){
                    mBinding.txtNoConfigMessage.setVisibility(View.VISIBLE);
                }
            }
        };
        mOnEditClickListener = new OnListItemClickListener<ConfigListModel>() {
            @Override
            public void onItemClicked(View view, ConfigListModel model, int position) {
                openEditConfigActivity(model,position);
            }
        };

        mOnConfigChangeListener = new ConfigListAdapter.OnConfigChangeListener() {
            @Override
            public void onConfigChanged(ConfigListModel model) {
                PrefsUtil.setSelectedConfig(requireContext(),model);
            }
        };
    }



    private void openEditConfigActivity(ConfigListModel model, int position) {
        Intent intent = null;
        if (Objects.equals(model.type, SSHConfig.CONFIG_TYPE)){
             intent = new Intent(requireContext(),AddSSHConfigActivity.class);
        } else if (Objects.equals(model.type, OpenVpnConfig.CONFIG_TYPE)) {
            intent = new Intent(requireContext(),AddOpenVpnConfigActivity.class);
        }
        if (intent == null){
            return;
        }
        intent.putExtra(AddSSHConfigActivity.KEY_MODE,AddSSHConfigActivity.MODE_EDIT);
        intent.putExtra(AddSSHConfigActivity.KEY_CONFIG_MODEL,new Gson().toJson(model));
        mAddConfigLauncher.launch(intent);
    }

    private void showUndoDeleteAction() {
        if (undoSnackBar.isShown()){
            undoSnackBar.dismiss();
        }
        undoSnackBar.show();
    }

    private void initViews() {
        Util.setStatusBarPaddingToView(mBinding.appbar);
        undoSnackBar=Snackbar.make(mBinding.getRoot(),R.string.undo_delete_config,4000);
        undoSnackBar.setAction(R.string.undo, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addConfigListModel(mLastDeletedConfig,mLastDeletedConfigPosition);

            }
        });
        mBinding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_add){
                openAddConfigActivity(OpenVpnConfig.CONFIG_TYPE);
            } else if (item.getItemId() == R.id.action_import) {
                openImportActivity();
            }
            return true;
        });
        if (mConfigListModels.isEmpty()){
            mBinding.txtNoConfigMessage.setVisibility(View.VISIBLE);
        }else {
            mBinding.txtNoConfigMessage.setVisibility(View.GONE);
        }
    }

    private void openImportActivity() {
        importActivityLuncher.launch("*/*");
    }
    private void processImport(Uri uri) {
        if (uri == null){
            return;
        }
        AlertDialog progressDialog = AlertUtil.showLoadingDialog(requireActivity());
        try {
            final List<ConfigListModel> currentConfigs = PrefsUtil.getAllConfigs(requireContext().getApplicationContext());
            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            ConfigsUtil.importConfig(inputStream, new OnCompleteListener<Pair<String, String>>() {
                @Override
                public void onComplete(Pair<String, String> result) {
                    progressDialog.dismiss();
                    if (result == null){
                        AlertUtil.showToast(getContext(), R.string.error_parsing_config, Toast.LENGTH_SHORT, AlertUtil.Type.ERROR);
                    }else {
                        Config config = null;
                        if (Objects.equals(result.first, SSHConfig.CONFIG_TYPE)){
                            config = new Gson().fromJson(result.second,SSHConfig.class);

                        }else if (Objects.equals(result.first, OpenVpnConfig.CONFIG_TYPE)){
                            config = OpenVpnConfig.fromJson(result.second);

                        }
                        if (config == null){
                            AlertUtil.showToast(getContext(), R.string.config_not_supported, Toast.LENGTH_SHORT, AlertUtil.Type.ERROR);
                            return;
                        }
                        ConfigListModel model = new ConfigListModel(config.getName(),
                                config.getId(),mAdapter.getItemCount()==0,
                                config.getType());
                        model.note = config.getNote();
                        if (currentConfigs.contains(model)){
                            AlertUtil.showToast(requireContext(), R.string.config_already_exists, Toast.LENGTH_SHORT, AlertUtil.Type.WARNING);
                            return;
                        }
                        addConfigListModel(model,null);
                        AlertUtil.showToast(requireContext(),R.string.config_imported_successfully,Toast.LENGTH_SHORT, AlertUtil.Type.SUCCESS);
                        try {
                            ConfigsUtil.saveConfig(requireContext(),config);
                        } catch (IOException e) {
                            AlertUtil.showToast(requireContext(), R.string.error_parsing_config, Toast.LENGTH_SHORT, AlertUtil.Type.ERROR);
                        }
                    }
                }

                @Override
                public void onException(Exception e) {
                    progressDialog.dismiss();
                    if (e instanceof ConfigNotSupportException){
                       AlertUtil.showToast(getContext(), R.string.config_not_supported, Toast.LENGTH_SHORT, AlertUtil.Type.ERROR);
                   }else {
                       AlertUtil.showToast(getContext(), R.string.error_parsing_config, Toast.LENGTH_SHORT, AlertUtil.Type.ERROR);
                   }
                }
            });
        } catch (Exception e) {
            AlertUtil.showToast(getContext(), R.string.error_parsing_config, Toast.LENGTH_SHORT, AlertUtil.Type.ERROR);
        }

    }


    private void addConfigListModel(ConfigListModel model, Integer configPosition){
        if (mAdapter != null){
            if (mAdapter.getItemCount() == 0){
                model.setSelected(true);
                PrefsUtil.setSelectedConfig(requireContext(),model);
            }
            if (configPosition != null){
                mAdapter.addConfig(model,configPosition);
            }else {
                mAdapter.addConfig(model);
            }
        }
        PrefsUtil.addConfig(requireContext(),model);
        mBinding.txtNoConfigMessage.setVisibility(View.GONE);
    }

    private void openAddConfigActivity(String type) {
        Intent intent = null;
        if (Objects.equals(type, SSHConfig.CONFIG_TYPE)){
            intent = new Intent(requireContext(),AddSSHConfigActivity.class);
        } else if (Objects.equals(type, OpenVpnConfig.CONFIG_TYPE)) {
            intent = new Intent(requireContext(),AddOpenVpnConfigActivity.class);
        }
        if (intent == null){
            return;
        }
        intent.putExtra(AddSSHConfigActivity.KEY_MODE,AddSSHConfigActivity.MODE_ADD);
        mAddConfigLauncher.launch(intent);
    }
}
