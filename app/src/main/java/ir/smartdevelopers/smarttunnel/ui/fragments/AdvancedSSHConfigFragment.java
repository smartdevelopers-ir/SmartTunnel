package ir.smartdevelopers.smarttunnel.ui.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import ir.smartdevelopers.smarttunnel.databinding.FragmentAdvancedSshConfigBinding;
import ir.smartdevelopers.smarttunnel.databinding.SshProxyLayoutBinding;
import ir.smartdevelopers.smarttunnel.ui.classes.SimpleTextWatcher;
import ir.smartdevelopers.smarttunnel.ui.models.SSHConfig;
import ir.smartdevelopers.smarttunnel.ui.utils.SSHConfigViewUtil;
import ir.smartdevelopers.smarttunnel.ui.viewModels.AddSSHConfigViewModel;

public class AdvancedSSHConfigFragment extends Fragment {
    public static final String KEY_SHOW_ERROR = " show_error";
    private FragmentAdvancedSshConfigBinding mBinding;
    private AddSSHConfigViewModel mViewModel;
    private SSHConfigViewUtil mConfigViewUtil;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentAdvancedSshConfigBinding.inflate(inflater,container,false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewModel = new ViewModelProvider(requireActivity()).get(AddSSHConfigViewModel.class);

        initViews();
    }

    private void initViews() {
        mBinding.edtUDPGWPort.addTextChangedListener(new SimpleTextWatcher( mBinding.edtUDPGWPort, mBinding.edtUDPGWPortLayout) {
            @Override
            public void onTextChanged(CharSequence text) {
                if (!TextUtils.isEmpty(text)){
                   mViewModel.getSSHConfigBuilder().setUDPGWPort(Integer.parseInt(text.toString()));
                }else {
                    mViewModel.getSSHConfigBuilder().setUDPGWPort(0);
                }
            }
        });
        mBinding.radDirect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked){
                    setDirect();
                }
            }
        });
        mBinding.radSshProxy.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked){
                    setSSHProxy();
                }
            }
        });
        if (mViewModel.getSSHConfigBuilder().getConnectionType() == SSHConfig.CONNECTION_TYPE_DIRECT){
            setDirect();
        } else if (mViewModel.getSSHConfigBuilder().getConnectionType() == SSHConfig.CONNECTION_TYPE_SSH_PROXY) {
            mBinding.sshConnectionTypeGroup.check(mBinding.radSshProxy.getId());
        } else if (mViewModel.getSSHConfigBuilder().getConnectionType() == SSHConfig.CONNECTION_TYPE_WEBSOCKET) {
            mBinding.sshConnectionTypeGroup.check(mBinding.radWebsocket.getId());
        } else if (mViewModel.getSSHConfigBuilder().getConnectionType() == SSHConfig.CONNECTION_TYPE_WEBSOCKET) {
            mBinding.sshConnectionTypeGroup.check(mBinding.radWebsocket.getId());
        }
        if (getArguments() != null){
            if (getArguments().getBoolean(KEY_SHOW_ERROR)){
                showErrors();
            }
        }
    }

    private void setSSHProxy() {
        mBinding.typeContainer.removeAllViewsInLayout();
        SshProxyLayoutBinding jumperBinding = SshProxyLayoutBinding.inflate(LayoutInflater.from(requireContext()),
                mBinding.typeContainer,true);
        mConfigViewUtil = new SSHConfigViewUtil(requireContext(),jumperBinding.jumperServer,mViewModel.getJumperConfigBuilder());
        mConfigViewUtil.initSshConfigViews();
        mViewModel.getSSHConfigBuilder().setPayload(null);
        mViewModel.getSSHConfigBuilder().setConnectionType(SSHConfig.CONNECTION_TYPE_SSH_PROXY);
        mViewModel.getSSHConfigBuilder().setServerNameIndicator(null);
    }

    private void setDirect() {
        mBinding.typeContainer.removeAllViews();
        mViewModel.clearJumper();
        mViewModel.getSSHConfigBuilder().setPayload(null);
        mViewModel.getSSHConfigBuilder().setConnectionType(SSHConfig.CONNECTION_TYPE_DIRECT);
        mViewModel.getSSHConfigBuilder().setServerNameIndicator(null);
    }

    public void showErrors(){
        if (mBinding.radSshProxy.isChecked()){
            mConfigViewUtil.showErrors(true);
        }


    }
}
