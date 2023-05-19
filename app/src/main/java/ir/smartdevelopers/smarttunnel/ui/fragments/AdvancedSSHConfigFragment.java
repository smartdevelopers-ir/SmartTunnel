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
import ir.smartdevelopers.smarttunnel.ui.viewModels.SshConfigVieModel;

public class AdvancedSSHConfigFragment extends Fragment {
    public static final String KEY_SHOW_ERROR = " show_error";
    private FragmentAdvancedSshConfigBinding mBinding;
    private AddSSHConfigViewModel mViewModel;
    private SshConfigVieModel mSshConfigVieModel;
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
        mSshConfigVieModel = new ViewModelProvider(requireActivity()).get(SshConfigVieModel.class);

        initViews();
    }

    private void initViews() {
        if (mSshConfigVieModel.getSSHConfigBuilder().isConnectionModeLocked()){
            mBinding.sshConnectionTypeGroup.setEnabled(false);
            if (mViewModel.getJumperConfigBuilder() != null){
                mViewModel.getJumperConfigBuilder()
                        .setServerAddressLocked(true)
                        .setServerPortLocked(true)
                        .setUsernameLocked(true)
                        .setPasswordLocked(true)
                        .setPrivateKeyLocked(true);
            }
        }
        mBinding.chbUseRemoteSocksServer.setOnClickListener(v->{
            mSshConfigVieModel.getSSHConfigBuilder().setUseRemoteSocksServer(mBinding.chbUseRemoteSocksServer.isChecked());
            if (mBinding.chbUseRemoteSocksServer.isChecked()){
                mBinding.remoteSocksServerContainer.setVisibility(View.VISIBLE);
            }else {
                mBinding.remoteSocksServerContainer.setVisibility(View.GONE);
            }
        });
        if (mSshConfigVieModel.getSSHConfigBuilder().isUseRemoteSocksServer()){
            mBinding.chbUseRemoteSocksServer.setChecked(true);
            mBinding.remoteSocksServerContainer.setVisibility(View.VISIBLE);
            mBinding.edtRemoteSocksAddress.setText(mSshConfigVieModel.getSSHConfigBuilder().getRemoteSocksAddress());
            mBinding.edtRemoteSocksPort.setText(String.valueOf(mSshConfigVieModel.getSSHConfigBuilder().getRemoteSocksPort()));
        }else {
            mBinding.chbUseRemoteSocksServer.setChecked(false);
            mBinding.remoteSocksServerContainer.setVisibility(View.GONE);
        }
        mBinding.edtRemoteSocksAddress.addTextChangedListener(new SimpleTextWatcher(mBinding.edtRemoteSocksAddress,mBinding.edtRemoteSocksAddressLayout) {
            @Override
            public void onTextChanged(CharSequence text) {
                if (text == null){
                    text = "";
                }
                mSshConfigVieModel.getSSHConfigBuilder().setRemoteSocksAddress(text.toString());
            }
        });
        mBinding.edtRemoteSocksPort.addTextChangedListener(new SimpleTextWatcher(mBinding.edtRemoteSocksPort,mBinding.edtRemoteSocksPortLayout) {
            @Override
            public void onTextChanged(CharSequence text) {

                if (!TextUtils.isEmpty(text)){
                    mSshConfigVieModel.getSSHConfigBuilder().setRemoteSocksPort(Integer.parseInt(text.toString()));
                }else {
                    mSshConfigVieModel.getSSHConfigBuilder().setRemoteSocksPort(0);
                }
            }
        });

        String udpgwPort = "7300";
        if (mSshConfigVieModel.getSSHConfigBuilder().getUDPGWPort() > 0){
            udpgwPort = String.valueOf(mSshConfigVieModel.getSSHConfigBuilder().getUDPGWPort());
        }
        mBinding.edtUDPGWPort.setText(udpgwPort);
        mBinding.edtUDPGWPort.addTextChangedListener(new SimpleTextWatcher( mBinding.edtUDPGWPort, mBinding.edtUDPGWPortLayout) {
            @Override
            public void onTextChanged(CharSequence text) {
                if (!TextUtils.isEmpty(text)){
                    mSshConfigVieModel.getSSHConfigBuilder().setUDPGWPort(Integer.parseInt(text.toString()));
                }else {
                    mSshConfigVieModel.getSSHConfigBuilder().setUDPGWPort(0);
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
        if (mSshConfigVieModel.getSSHConfigBuilder().getConnectionType() == SSHConfig.CONNECTION_TYPE_DIRECT){
            setDirect();
        } else if (mSshConfigVieModel.getSSHConfigBuilder().getConnectionType() == SSHConfig.CONNECTION_TYPE_SSH_PROXY) {
            mBinding.sshConnectionTypeGroup.check(mBinding.radSshProxy.getId(),true);
        } else if (mSshConfigVieModel.getSSHConfigBuilder().getConnectionType() == SSHConfig.CONNECTION_TYPE_WEBSOCKET) {
            mBinding.sshConnectionTypeGroup.check(mBinding.radWebsocket.getId(),true);
        } else if (mSshConfigVieModel.getSSHConfigBuilder().getConnectionType() == SSHConfig.CONNECTION_TYPE_WEBSOCKET) {
            mBinding.sshConnectionTypeGroup.check(mBinding.radWebsocket.getId(),true);
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
        mSshConfigVieModel.getSSHConfigBuilder().setPayload(null);
        mSshConfigVieModel.getSSHConfigBuilder().setConnectionType(SSHConfig.CONNECTION_TYPE_SSH_PROXY);
        mSshConfigVieModel.getSSHConfigBuilder().setServerNameIndicator(null);
    }

    private void setDirect() {
        mBinding.typeContainer.removeAllViews();
        mViewModel.clearJumper();
        mSshConfigVieModel.getSSHConfigBuilder().setPayload(null);
        mSshConfigVieModel.getSSHConfigBuilder().setConnectionType(SSHConfig.CONNECTION_TYPE_DIRECT);
        mSshConfigVieModel.getSSHConfigBuilder().setServerNameIndicator(null);
        mSshConfigVieModel.getSSHConfigBuilder().setJumper(null);
    }

    public void showErrors(){
        if (mBinding.radSshProxy.isChecked()){
            mConfigViewUtil.showErrors(true);
        }


    }
}
