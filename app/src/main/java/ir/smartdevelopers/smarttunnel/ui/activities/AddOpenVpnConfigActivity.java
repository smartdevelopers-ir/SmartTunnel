package ir.smartdevelopers.smarttunnel.ui.activities;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.tabs.TabLayout;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.UUID;

import de.blinkt.openvpn.VpnProfile;
import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.databinding.ActivityAddOpenvpnConfigBinding;
import ir.smartdevelopers.smarttunnel.ui.fragments.ExportOpenVPNConfigDialog;
import ir.smartdevelopers.smarttunnel.ui.fragments.OpenVpnProfileFragment;
import ir.smartdevelopers.smarttunnel.ui.fragments.SimpleSSHConfigFragment;
import ir.smartdevelopers.smarttunnel.ui.models.ConfigListModel;
import ir.smartdevelopers.smarttunnel.ui.models.OpenVpnConfig;
import ir.smartdevelopers.smarttunnel.ui.models.SSHConfig;
import ir.smartdevelopers.smarttunnel.ui.utils.ConfigsUtil;
import ir.smartdevelopers.smarttunnel.ui.viewModels.AddOpenVPNConfigViewModel;
import ir.smartdevelopers.smarttunnel.ui.viewModels.SshConfigVieModel;
import ir.smartdevelopers.smarttunnel.utils.Logger;

public class AddOpenVpnConfigActivity extends AppCompatActivity {
    public static final String KEY_MODE = "start_mode";
    public static final String KEY_CONFIG_MODEL = "config";
    public static final int MODE_ADD = 1;
    public static final int MODE_EDIT = 2;

    private ActivityAddOpenvpnConfigBinding mBinding;
    private AddOpenVPNConfigViewModel mViewModel;
    private SshConfigVieModel mSshConfigVieModel;
    private ConfigListModel editingConfigModel;
    private boolean tabSelectedByUser = true;
    private ActivityResultLauncher<String> storagePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
            new ActivityResultCallback<Boolean>() {
                @Override
                public void onActivityResult(Boolean isGranted) {
                    if (isGranted){
                        openExportDialog();
                    }
                }
            });



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityAddOpenvpnConfigBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        mViewModel = new ViewModelProvider(this).get(AddOpenVPNConfigViewModel.class);
        mSshConfigVieModel = new ViewModelProvider(this).get(SshConfigVieModel.class);
        initViews();
        showSSHSettingsFragment(false,null,false);
    }

    private void initViews() {
//        Util.setStatusBarPaddingToView(mBinding.appbar);
        mBinding.btnSave.setOnClickListener(v -> {
            saveConfig();
        });
        mBinding.btnExport.setOnClickListener(v -> {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        });
        mBinding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tabSelectedByUser){
                    if (tab.getPosition() == 0) {
                        showSSHSettingsFragment(true,null,false);
                    } else {
                        showOpenVpnProfileSettingsFragment(true,null,false);
                    }
                }else {
                    tabSelectedByUser = true;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
        int mode = getIntent().getIntExtra(KEY_MODE,MODE_ADD);
        if (mode == MODE_EDIT){
            String configJson = getIntent().getStringExtra(KEY_CONFIG_MODEL);
            if (!TextUtils.isEmpty(configJson)){
                editingConfigModel = new Gson().fromJson(configJson,ConfigListModel.class);
                try {
                    OpenVpnConfig config = (OpenVpnConfig) ConfigsUtil.loadConfig(this,editingConfigModel.configId,editingConfigModel.type);
                    if (config != null){
                        OpenVpnConfig.Builder builder = config.toBuilder();
                        mViewModel.setOpenVpnProfile(builder.getProfile());
                        mSshConfigVieModel.setSSHConfig(getSshConfig(builder));

                    }

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }

    private SSHConfig.Builder getSshConfig(OpenVpnConfig.Builder builder) {
        SSHConfig.Builder configBuilder = new SSHConfig.Builder(builder.getName(),
                SSHConfig.MODE_MAIN_CONNECTION,builder.getServerAddress(),
                builder.getServerPort(),builder.getUsername(),builder.getPassword());
        configBuilder.setId(builder.getId())
                .setPrivateKey(builder.getPrivateKey())
                .setUsePrivateKey(builder.isUsePrivateKey())
                .setServerAddressLocked(builder.isServerAddressLocked())
                .setServerPortLocked(builder.isServerPortLocked())
                .setUsernameLocked(builder.isUsernameLocked())
                .setPasswordLocked(builder.isPasswordLocked())
                .setPrivateKeyLocked(builder.isPrivateKeyLocked())
                .setPreferIPv6(builder.isPreferIPv6());
        return configBuilder;
    }

    private void showOpenVpnProfileSettingsFragment(boolean animate, Bundle data,boolean selectTab) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (animate) {
            transaction.setCustomAnimations(R.anim.fragment_slide_right_to_left_enter, R.anim.fragment_slide_right_to_left_exit);
        }
       transaction.replace(R.id.sshConfigFragmentContainer, OpenVpnProfileFragment.class,data)
                .commit();
        if (selectTab) {
            tabSelectedByUser = false;
            mBinding.tabLayout.selectTab(mBinding.tabLayout.getTabAt(1));
        }
    }

    private void showSSHSettingsFragment(boolean animate, Bundle data,boolean selectTab) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (animate) {
            transaction.setCustomAnimations(R.anim.fragment_slide_left_to_right_enter, R.anim.fragment_slide_left_to_right_exit);
        }
        transaction.replace(R.id.sshConfigFragmentContainer, SimpleSSHConfigFragment.class,data)
                .commit();
        if (selectTab) {
            tabSelectedByUser = false;
            mBinding.tabLayout.selectTab(mBinding.tabLayout.getTabAt(0));
        }
    }

    private void openExportDialog() {
        if (checkConfig()){
            getSupportFragmentManager().beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .addToBackStack("export_config")
                    .add(android.R.id.content, ExportOpenVPNConfigDialog.getInstance())
                    .commit();
        }
    }

    private void saveConfig() {
        ConfigListModel configListModel= null;
        if (checkConfig()){
            String id = mSshConfigVieModel.getSSHConfigBuilder().getId();
            if (id == null){
                id = UUID.randomUUID().toString();
                mSshConfigVieModel.getSSHConfigBuilder().setId(id);
            }
            OpenVpnConfig config = mViewModel.generateConfig(mSshConfigVieModel.getSSHConfigBuilder(),id);

            try {
                ConfigsUtil.saveConfig(getApplicationContext(),config);
                if (editingConfigModel != null){
                    configListModel = editingConfigModel;
                    configListModel.name = config.getName();
                }else {
                    configListModel = new ConfigListModel(config.getName(), config.getId(), false, OpenVpnConfig.CONFIG_TYPE);
                }
            } catch (IOException e) {
                Logger.logError(getString(R.string.can_not_save_config) + " : "+e.getMessage());
            }
            if (configListModel != null){
                String json = new Gson().toJson(configListModel);
                Intent data = new Intent();
                data.putExtra(KEY_CONFIG_MODEL,json);
                data.putExtra(KEY_MODE,getIntent().getIntExtra(KEY_MODE,MODE_ADD));
                setResult(RESULT_OK,data);
                finish();
            }
        }


    }

    private boolean checkConfig(){
        SSHConfig.Builder builder = mSshConfigVieModel.getSSHConfigBuilder();
        if (
                TextUtils.isEmpty(builder.getServerAddress()) ||
                        TextUtils.isEmpty(builder.getName()) ||
                        TextUtils.isEmpty(builder.getUsername()) ||
                        builder.getServerPort() == 0 ||
                        (builder.isUsePrivateKey() ? builder.getPrivateKey() == null : TextUtils.isEmpty(builder.getPassword()))

        ) {
            showSSHConfigErrors();
            return false;
        }
        VpnProfile profile = mViewModel.getOpenVpnProfile();
        boolean openVpnError = false;
        if (profile == null){
            openVpnError = true;
        }else if (profile.mAuthenticationType == VpnProfile.TYPE_USERPASS &&
                ( TextUtils.isEmpty(profile.mPassword) ||TextUtils.isEmpty(profile.mUsername) ) ){
            openVpnError = true;
        }
        if (openVpnError){
            showOpenVpnErrors();
            return false;
        }

        return true;
    }

    private void showOpenVpnErrors() {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.sshConfigFragmentContainer);
        if (current instanceof OpenVpnProfileFragment){
            ((OpenVpnProfileFragment) current).showErrors();
        }else {
            Bundle args = new Bundle();
            args.putBoolean(OpenVpnProfileFragment.KEY_SHOW_ERROR,true);
            showOpenVpnProfileSettingsFragment(true,args,true);
        }
    }


    private void showSSHConfigErrors() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.sshConfigFragmentContainer);
        if ( currentFragment instanceof SimpleSSHConfigFragment){
            ((SimpleSSHConfigFragment) currentFragment).showErrors();
        }else {
            Bundle args = new Bundle();
            args.putBoolean(SimpleSSHConfigFragment.KEY_SHOW_ERROR,true);
            showSSHSettingsFragment(true,args,true);
        }
    }
}