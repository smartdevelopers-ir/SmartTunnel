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

import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.databinding.ActivityAddSshconfigBinding;
import ir.smartdevelopers.smarttunnel.ui.fragments.AdvancedSSHConfigFragment;
import ir.smartdevelopers.smarttunnel.ui.fragments.ExportSshConfigDialog;
import ir.smartdevelopers.smarttunnel.ui.fragments.SimpleSSHConfigFragment;
import ir.smartdevelopers.smarttunnel.ui.models.ConfigListModel;
import ir.smartdevelopers.smarttunnel.ui.models.SSHConfig;
import ir.smartdevelopers.smarttunnel.ui.models.SSHProxy;
import ir.smartdevelopers.smarttunnel.ui.utils.ConfigsUtil;
import ir.smartdevelopers.smarttunnel.ui.viewModels.AddSSHConfigViewModel;
import ir.smartdevelopers.smarttunnel.ui.viewModels.SshConfigVieModel;
import ir.smartdevelopers.smarttunnel.utils.Logger;

public class AddSSHConfigActivity extends AppCompatActivity {
    public static final String KEY_MODE = "start_mode";
    public static final String KEY_CONFIG_MODEL = "config";
    public static final int MODE_ADD = 1;
    public static final int MODE_EDIT = 2;

    private ActivityAddSshconfigBinding mBinding;
    private AddSSHConfigViewModel mViewModel;
    private SshConfigVieModel mSshConfigVieModel;
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
        mBinding = ActivityAddSshconfigBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        mViewModel = new ViewModelProvider(this).get(AddSSHConfigViewModel.class);
        mSshConfigVieModel = new ViewModelProvider(this).get(SshConfigVieModel.class);
        initViews();
        showSimpleSSHSettingsFragment(false,null);
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
                if (tab.getPosition() == 0) {
                    showSimpleSSHSettingsFragment(true,null);
                } else {
                    showAdvancedSSHSettingsFragment(true,null);
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
                ConfigListModel model = new Gson().fromJson(configJson,ConfigListModel.class);
                try {
                    SSHConfig config = (SSHConfig) ConfigsUtil.loadConfig(this,model.configId,model.type);
                    if (config != null){
                        SSHConfig.Builder builder = config.toBuilder();
                        mSshConfigVieModel.setSSHConfig(builder);
                        if (config.getJumper() != null) {
                            mViewModel.setJumperConfigBuilder(config.getJumper().getSSHConfig().toBuilder());
                        }
                    }

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void showAdvancedSSHSettingsFragment(boolean animate,Bundle data) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (animate) {
            transaction.setCustomAnimations(R.anim.fragment_slide_right_to_left_enter, R.anim.fragment_slide_right_to_left_exit);
        }
       transaction.replace(R.id.sshConfigFragmentContainer, AdvancedSSHConfigFragment.class,data)
                .commit();

    }

    private void showSimpleSSHSettingsFragment(boolean animate,Bundle data) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (animate) {
            transaction.setCustomAnimations(R.anim.fragment_slide_left_to_right_enter, R.anim.fragment_slide_left_to_right_exit);
        }
        transaction.replace(R.id.sshConfigFragmentContainer, SimpleSSHConfigFragment.class,data)
                .commit();
    }

    private void openExportDialog() {
        if (checkConfig()){
            getSupportFragmentManager().beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .addToBackStack("export_config")
                    .add(android.R.id.content,
                            ExportSshConfigDialog.getInstance(mSshConfigVieModel.getSSHConfigBuilder().getName()))
                    .commit();
        }
    }

    private void saveConfig() {
        ConfigListModel configListModel= null;
        if (checkConfig()){
            SSHConfig config = generateConfig();
            try {
                ConfigsUtil.saveConfig(getApplicationContext(),config);
                configListModel = new ConfigListModel(config.getName(),config.getId(),false,SSHConfig.CONFIG_TYPE);
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
    /** Before calling this, call {@link #checkConfig()}*/
    private SSHConfig generateConfig(){
        SSHConfig.Builder builder = mSshConfigVieModel.getSSHConfigBuilder();
        if (builder.getConnectionType() == SSHConfig.CONNECTION_TYPE_SSH_PROXY){
            SSHProxy jumper = new SSHProxy(mViewModel.getJumperConfigBuilder().build());
            builder.setJumper(jumper);
        }
        return builder.build();
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
        if (builder.getConnectionType() == SSHConfig.CONNECTION_TYPE_SSH_PROXY) {
            SSHConfig.Builder jumper = mViewModel.getJumperConfigBuilder();
            if (TextUtils.isEmpty(jumper.getServerAddress()) ||
                    TextUtils.isEmpty(jumper.getUsername()) ||
                    jumper.getServerPort() == 0 ||
                    (jumper.isUsePrivateKey() ? jumper.getPrivateKey() == null : TextUtils.isEmpty(jumper.getPassword()))

            ){
                showJumperErrors();
                return false;
            }
        }
        return true;
    }

    private void showJumperErrors() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.sshConfigFragmentContainer);
        if ( currentFragment instanceof AdvancedSSHConfigFragment){
            ((AdvancedSSHConfigFragment) currentFragment).showErrors();
        }else {
            Bundle args = new Bundle();
            args.putBoolean(AdvancedSSHConfigFragment.KEY_SHOW_ERROR,true);
            showAdvancedSSHSettingsFragment(true,args);
        }
    }

    private void showSSHConfigErrors() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.sshConfigFragmentContainer);
        if ( currentFragment instanceof SimpleSSHConfigFragment){
            ((SimpleSSHConfigFragment) currentFragment).showErrors();
        }else {
            Bundle args = new Bundle();
            args.putBoolean(SimpleSSHConfigFragment.KEY_SHOW_ERROR,true);
            showSimpleSSHSettingsFragment(true,args);
        }
    }
}