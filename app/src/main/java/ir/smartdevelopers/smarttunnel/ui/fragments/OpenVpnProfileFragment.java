package ir.smartdevelopers.smarttunnel.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.Connection;
import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.databinding.FragmentOpenVpnProfileBinding;
import ir.smartdevelopers.smarttunnel.ui.classes.SimpleTextWatcher;
import ir.smartdevelopers.smarttunnel.ui.utils.AlertUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.Util;
import ir.smartdevelopers.smarttunnel.ui.viewModels.AddOpenVPNConfigViewModel;

public class OpenVpnProfileFragment extends Fragment {
    public static final String KEY_SHOW_ERROR = "key_show_error";
    private FragmentOpenVpnProfileBinding mBinding;
    private AddOpenVPNConfigViewModel mViewModel;
    private ActivityResultLauncher<String> configPicker = registerForActivityResult(new ActivityResultContracts.GetContent(){
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @NonNull String input) {
            Intent intent= super.createIntent(context, input);
//            HashSet<String> mimeTypes = new HashSet<>(Arrays.asList("application/x-openvpn-profile",
//                    "application/openvpn-profile",
//                    "application/ovpn" ));
//
//            intent.putExtra(Intent.EXTRA_MIME_TYPES,mimeTypes);
            return intent;
        }
    }, new ActivityResultCallback<Uri>() {
        @Override
        public void onActivityResult(Uri result) {
            if (result!=null){
                parsConfig(result);
            }
        }
    });



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentOpenVpnProfileBinding.inflate(inflater,container,false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewModel = new ViewModelProvider(requireActivity()).get(AddOpenVPNConfigViewModel.class);
        initViews();
    }

    private void initViews() {
        mBinding.btnChooseProfile.setOnClickListener(v->{
            openFilePicker();
        });
        if (mViewModel.getOpenVpnProfile() != null){
            fillViews(mViewModel.getOpenVpnProfile());
        }else {
            enableInputs(false);
        }
        mBinding.edtServerAddress.addTextChangedListener(new SimpleTextWatcher(mBinding.edtServerAddress,mBinding.edtServerAddressLayout) {
            @Override
            public void onTextChanged(CharSequence text) {
                if (text == null){
                    text = "";
                }
                if (mViewModel.getOpenVpnProfile().mConnections != null && mViewModel.getOpenVpnProfile().mConnections.length >0){
                    mViewModel.getOpenVpnProfile().mConnections[0].mServerName = text.toString();
                }
            }
        });
        mBinding.edtServerPort.addTextChangedListener(new SimpleTextWatcher( mBinding.edtServerPort, mBinding.edtServerPortLayout) {
            @Override
            public void onTextChanged(CharSequence text) {
                if (text == null){
                    text = "";
                }
                if (mViewModel.getOpenVpnProfile().mConnections != null && mViewModel.getOpenVpnProfile().mConnections.length >0){
                    mViewModel.getOpenVpnProfile().mConnections[0].mServerPort = text.toString();
                }
            }
        });
        mBinding.edtPassword.addTextChangedListener(new SimpleTextWatcher(mBinding.edtPassword,mBinding.edtPasswordLayout) {
            @Override
            public void onTextChanged(CharSequence text) {
                VpnProfile profile = mViewModel.getOpenVpnProfile();
                if (profile == null){
                    return;
                }
                if (text == null){
                    text = "";
                }
                profile.mPassword = text.toString();
            }
        });
        mBinding.edtUsername.addTextChangedListener(new SimpleTextWatcher(mBinding.edtUsername,mBinding.edtUsernameLayout) {
            @Override
            public void onTextChanged(CharSequence text) {
                VpnProfile profile = mViewModel.getOpenVpnProfile();
                if (profile == null){
                    return;
                }
                if (text == null){
                    text = "";
                }
                profile.mUsername = text.toString();
            }
        });

        if (getArguments() != null){
            boolean showError = getArguments().getBoolean(KEY_SHOW_ERROR);
            if (showError){
                showErrors();
            }
        }

    }

    private void enableInputs(boolean enable) {
        mBinding.edtServerAddressLayout.setEnabled(enable);
        mBinding.edtServerPortLayout.setEnabled(enable);
        mBinding.edtUsernameLayout.setEnabled(enable);
        mBinding.edtPasswordLayout.setEnabled(enable);
        if (enable){
            mBinding.edtServerAddress.setHint("");
            mBinding.edtServerPort.setHint("");
            mBinding.edtUsername.setHint("");
            mBinding.edtPassword.setHint("");
        }else {
            mBinding.edtServerAddress.setHint(R.string.import_config);
            mBinding.edtServerPort.setHint(R.string.import_config);
            mBinding.edtUsername.setHint(R.string.import_config);
            mBinding.edtPassword.setHint(R.string.import_config);
        }
    }

    public void showErrors() {
        VpnProfile profile = mViewModel.getOpenVpnProfile();
        if (profile == null){
            AlertUtil.showToast(requireContext(),R.string.openvpn_profile_is_empty,Toast.LENGTH_SHORT, AlertUtil.Type.ERROR);
            return;
        }
        if (profile.mConnections == null || profile.mConnections.length ==0){
            AlertUtil.showToast(requireContext(),R.string.no_connection_detail_in_openvpn_config,Toast.LENGTH_SHORT, AlertUtil.Type.ERROR);
        }else {
            Connection connection = profile.mConnections[0];
            if (TextUtils.isEmpty(connection.mServerName) || TextUtils.isEmpty(mBinding.edtServerAddress.getText())){
                mBinding.edtServerAddressLayout.setError(getString(R.string.enter_server_address));
            }
            if (TextUtils.isEmpty(connection.mServerPort) || TextUtils.isEmpty(mBinding.edtServerPort.getText())){
                mBinding.edtServerPortLayout.setError(getString(R.string.enter_server_port));
            }
        }

        if (profile.mAuthenticationType == VpnProfile.TYPE_USERPASS){
            if (TextUtils.isEmpty(mBinding.edtUsername.getText())){
                mBinding.edtUsernameLayout.setError(getString(R.string.enter_username));
            }
            if (TextUtils.isEmpty(mBinding.edtPassword.getText())){
                mBinding.edtPasswordLayout.setError(getString(R.string.enter_password));
            }
        }
    }

    private void openFilePicker() {
        configPicker.launch("*/*");
    }
    private void parsConfig(Uri uri) {
        Util.SINGLE_EXECUTOR_SERVICE.execute(()->{
            try {
                InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                ConfigParser parser = new ConfigParser();
                parser.parseConfig(new InputStreamReader(inputStream));
                VpnProfile profile = parser.convertProfile();

                inputStream.close();
                Util.MAIN_HANDLER.post(()->{
                    mViewModel.setOpenVpnProfile(profile);
                    if (getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED){
                        return;
                    }
                    fillViews(profile);
                });
            } catch (ConfigParser.ConfigParseError | IOException e) {
                e.printStackTrace();
                Util.MAIN_HANDLER.post(()->{
                    if (getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED){
                        return;
                    }
                   AlertUtil.showToast(requireActivity(),R.string.selecet_curroct_openvpn_config,Toast.LENGTH_SHORT, AlertUtil.Type.ERROR);
                });
            }
        });
    }

    private void fillViews(VpnProfile profile) {
        if (profile.mConnections == null || profile.mConnections.length ==0){
            AlertUtil.showToast(requireActivity(), R.string.no_connection_detail_in_openvpn_config, Toast.LENGTH_LONG, AlertUtil.Type.ERROR);
            return;
        }
        enableInputs(true);
        Connection connection = profile.mConnections[0];
        if (connection.mUseUdp){
            AlertUtil.showAlertDialog(requireActivity(),getString( R.string.open_vpn_udp_error_message),
                    getString(R.string.warning), AlertUtil.Type.WARNING);
            connection.mUseUdp = false;

        }
        mBinding.edtServerAddress.setText(connection.mServerName);
        mBinding.edtPassword.setText(profile.mPassword);
        mBinding.edtServerPort.setText(connection.mServerPort);
        mBinding.edtUsername.setText(profile.mUsername);
        mBinding.radTcp.setChecked(true);
        mBinding.radTcp.jumpDrawablesToCurrentState();


    }

}
