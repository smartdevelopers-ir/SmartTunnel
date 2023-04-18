package ir.smartdevelopers.smarttunnel.ui.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.gson.Gson;

import ir.smartdevelopers.smarttunnel.databinding.FragmentProxyBinding;
import ir.smartdevelopers.smarttunnel.databinding.HttpProxyLayoutBinding;
import ir.smartdevelopers.smarttunnel.databinding.SshConfigLayoutBinding;
import ir.smartdevelopers.smarttunnel.ui.activities.SettingsActivity;
import ir.smartdevelopers.smarttunnel.ui.classes.SimpleTextWatcher;
import ir.smartdevelopers.smarttunnel.ui.interfaces.Savable;
import ir.smartdevelopers.smarttunnel.ui.models.HttpProxy;
import ir.smartdevelopers.smarttunnel.ui.models.ProxyType;
import ir.smartdevelopers.smarttunnel.ui.models.SSHConfig;
import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.SSHConfigViewUtil;

public class ProxyFragment extends Fragment implements Savable {
    private FragmentProxyBinding mBinding;
    private SSHConfig.Builder mSshConfigBuilder;
    private SSHConfigViewUtil configViewUtil;
    private HttpProxyLayoutBinding mHttpProxyLayoutBinding;
    private HttpProxy mHttpProxy;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
       mBinding = FragmentProxyBinding.inflate(inflater,container,false);
       return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews();
    }

    private void initViews() {
        mBinding.radNone.setOnClickListener(v->{
            if (mBinding.radNone.isChecked()){
                mBinding.proxyContainer.removeAllViews();
            }
        });
        mBinding.radHttp.setOnClickListener(v->{
            if (mHttpProxy == null){
                mHttpProxy = new HttpProxy("",0);
            }
            initHttpProxyViews(mHttpProxy);
        });
        mBinding.radSsh.setOnClickListener(v->{
            if (mSshConfigBuilder == null){
                mSshConfigBuilder = new SSHConfig.Builder(SSHConfig.MODE_PROXY);
            }
            loadSshProxyLayout(mSshConfigBuilder);
        });

        int proxyType = PrefsUtil.getGlobalProxyType(requireContext());
        if (proxyType == ProxyType.TYPE_NONE){
            mBinding.radNone.setChecked(true);
            mBinding.radNone.jumpDrawablesToCurrentState();
        } else if (proxyType == ProxyType.TYPE_HTTP) {
            mBinding.radHttp.setChecked(true);
            mBinding.radHttp.jumpDrawablesToCurrentState();
            String proxyJson = PrefsUtil.loadGlobalProxy(requireContext());
            if (!TextUtils.isEmpty(proxyJson)){
                mHttpProxy = new Gson().fromJson(proxyJson,HttpProxy.class);
                initHttpProxyViews(mHttpProxy);
            }
        }else if (proxyType == ProxyType.TYPE_SSH){
            mBinding.radSsh.setChecked(true);
            mBinding.radSsh.jumpDrawablesToCurrentState();
            String proxyJson = PrefsUtil.loadGlobalProxy(requireContext());
            if (!TextUtils.isEmpty(proxyJson)){
                SSHConfig sshProxy = new Gson().fromJson(proxyJson,SSHConfig.class);
                mSshConfigBuilder = sshProxy.toBuilder();
                loadSshProxyLayout(mSshConfigBuilder);
            }
        }
    }

    private void loadSshProxyLayout(SSHConfig.Builder builder) {
        mBinding.proxyContainer.removeAllViewsInLayout();
        SshConfigLayoutBinding binding = SshConfigLayoutBinding.inflate(LayoutInflater.from(requireContext()),
                mBinding.proxyContainer,false);
        mBinding.proxyContainer.addView(binding.getRoot());

        configViewUtil = new SSHConfigViewUtil(requireContext(),
                binding,builder);
        configViewUtil.initSshConfigViews();
    }

    private void initHttpProxyViews(HttpProxy proxy) {
        mBinding.proxyContainer.removeAllViewsInLayout();
        mHttpProxyLayoutBinding = HttpProxyLayoutBinding.inflate(LayoutInflater.from(requireContext()),
                mBinding.proxyContainer,false);
        mBinding.proxyContainer.addView(mHttpProxyLayoutBinding.getRoot());
        mHttpProxyLayoutBinding.edtServerAddress.addTextChangedListener(
                new SimpleTextWatcher(mHttpProxyLayoutBinding.edtServerAddress,mHttpProxyLayoutBinding.edtServerAddressLayout) {
            @Override
            public void onTextChanged(CharSequence text) {
                proxy.setAddress(text.toString());
            }
        });
        mHttpProxyLayoutBinding.edtServerPort.addTextChangedListener(
                new SimpleTextWatcher(mHttpProxyLayoutBinding.edtServerPort,mHttpProxyLayoutBinding.edtServerPortLayout) {
            @Override
            public void onTextChanged(CharSequence text) {
               if (!TextUtils.isEmpty(text)){
                   proxy.setPort(Integer.parseInt(text.toString()));
               }
            }
        });
        if (proxy != null){
            if (proxy.getPort() != 0){
                mHttpProxyLayoutBinding.edtServerPort.setText(String.valueOf(proxy.getPort()));
            }
            mHttpProxyLayoutBinding.edtServerAddress.setText(proxy.getAddress());
        }
    }

    @Override
    public void save() {

        if (mBinding.radNone.isChecked()){
            PrefsUtil.saveGlobalProxy(requireContext(),"");
            PrefsUtil.setGlobalProxyType(requireContext(),ProxyType.TYPE_NONE);
        }else if (mBinding.radHttp.isChecked()){
            if (mHttpProxyLayoutBinding != null){
                PrefsUtil.setGlobalProxyType(requireContext(),ProxyType.TYPE_HTTP);
                try{
                    String server = mHttpProxyLayoutBinding.edtServerAddress.getText().toString();
                    int port = Integer.parseInt(mHttpProxyLayoutBinding.edtServerPort.getText().toString());
                    HttpProxy proxy = new HttpProxy(server,port);
                    PrefsUtil.saveGlobalProxy(requireContext(),new Gson().toJson(proxy));
                }catch (Exception e){
                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
            }else {
                return;
            }
        }else if (mBinding.radSsh.isChecked()){
            if (!configViewUtil.showErrors(true)){
                SSHConfig config = mSshConfigBuilder.build();
                PrefsUtil.setGlobalProxyType(requireContext(),ProxyType.TYPE_SSH);
                PrefsUtil.saveGlobalProxy(requireContext(),new Gson().toJson(config));
            }else {
                return;
            }
        }
        requireActivity().onBackPressed();
    }

    private void goToSettingsFragment() {
        if (getActivity() instanceof SettingsActivity){
            ((SettingsActivity) getActivity()).goToSettingsFragment(true);
        }
    }
}
