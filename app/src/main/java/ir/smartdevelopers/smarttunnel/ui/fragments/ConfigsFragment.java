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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.databinding.FragmentConfigsBinding;
import ir.smartdevelopers.smarttunnel.ui.activities.AddSSHConfigActivity;
import ir.smartdevelopers.smarttunnel.ui.adapters.ConfigListAdapter;
import ir.smartdevelopers.smarttunnel.ui.interfaces.OnCompleteListener;
import ir.smartdevelopers.smarttunnel.ui.interfaces.OnListItemClickListener;
import ir.smartdevelopers.smarttunnel.ui.models.ConfigListModel;
import ir.smartdevelopers.smarttunnel.ui.models.SSHConfig;
import ir.smartdevelopers.smarttunnel.ui.utils.ConfigsUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;

public class ConfigsFragment extends Fragment {

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
        Intent intent = new Intent(requireContext(),AddSSHConfigActivity.class);
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
        undoSnackBar=Snackbar.make(mBinding.getRoot(),R.string.undo_delete_config,4000);
        undoSnackBar.setAction(R.string.undo, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAdapter != null){
                    mAdapter.addConfig(mLastDeletedConfig,mLastDeletedConfigPosition);
                }
                PrefsUtil.addConfig(requireContext(),mLastDeletedConfig);
            }
        });
        mBinding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_add){
                openAddConfigActivity();
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
        if (!uri.toString().endsWith(".st")){
            Toast.makeText(getContext(), R.string.config_not_supported, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            ConfigsUtil.importConfig(inputStream, new OnCompleteListener<Pair<String, String>>() {
                @Override
                public void onComplete(Pair<String, String> result) {
                    if (result == null){
                        Toast.makeText(getContext(), R.string.error_parsing_config, Toast.LENGTH_SHORT).show();
                    }else {
                        if (Objects.equals(result.first, SSHConfig.CONFIG_TYPE)){
                            SSHConfig config = new Gson().fromJson(result.second,SSHConfig.class);
                            ConfigListModel model = new ConfigListModel(config.getName(),
                                    config.getId(),mAdapter.getItemCount()==0,
                                    config.getType());
                            mAdapter.addConfig(model);
                            PrefsUtil.addConfig(requireContext(),model);
                            try {
                                ConfigsUtil.saveConfig(requireContext(),config);
                            } catch (IOException e) {
                                Toast.makeText(requireContext(), R.string.error_parsing_config, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
            });
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.error_parsing_config, Toast.LENGTH_SHORT).show();
        }
    }
    private void openAddConfigActivity() {
        Intent intent = new Intent(requireContext(),AddSSHConfigActivity.class);
        intent.putExtra(AddSSHConfigActivity.KEY_MODE,AddSSHConfigActivity.MODE_ADD);
        mAddConfigLauncher.launch(intent);
    }
}
