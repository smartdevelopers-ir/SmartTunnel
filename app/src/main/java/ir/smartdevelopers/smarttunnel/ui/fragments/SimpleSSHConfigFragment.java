package ir.smartdevelopers.smarttunnel.ui.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.databinding.FragmentSimpleSshConfigBinding;
import ir.smartdevelopers.smarttunnel.ui.classes.SimpleTextWatcher;
import ir.smartdevelopers.smarttunnel.ui.utils.SSHConfigViewUtil;
import ir.smartdevelopers.smarttunnel.ui.viewModels.AddSSHConfigViewModel;
import ir.smartdevelopers.smarttunnel.ui.viewModels.SshConfigVieModel;

public class SimpleSSHConfigFragment extends Fragment {
    public static final String KEY_SHOW_ERROR = " show_error";
    private FragmentSimpleSshConfigBinding mBinding;
    private SshConfigVieModel mViewModel;
    private SSHConfigViewUtil configViewUtil;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentSimpleSshConfigBinding.inflate(inflater,container,false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewModel = new ViewModelProvider(requireActivity()).get(SshConfigVieModel.class);
//        if (mViewModel.getSSHConfigBuilder() == null){
//            mViewModel.setSSHConfig(new SSHConfig.Builder(SSHConfig.MODE_MAIN_CONNECTION));
//        }
        initViews();
    }

    private void initViews() {
        configViewUtil = new SSHConfigViewUtil(requireContext(),mBinding.sshConfig,mViewModel.getSSHConfigBuilder());
        configViewUtil.initSshConfigViews();
        mBinding.edtConfigName.setText(mViewModel.getSSHConfigBuilder().getName());

        mBinding.edtConfigName.addTextChangedListener(new SimpleTextWatcher(mBinding.edtConfigName, mBinding.edtConfigNameLayout) {
            @Override
            public void onTextChanged(CharSequence text) {
                mViewModel.getSSHConfigBuilder().setName(text.toString());
            }
        });
        if (getArguments() != null){
            if (getArguments().getBoolean(KEY_SHOW_ERROR)){
                showErrors();
            }
        }

    }

    public void showErrors(){
        boolean sshConfigFocus = true;
        if (TextUtils.isEmpty(mViewModel.getSSHConfigBuilder().getName())){
            mBinding.edtConfigNameLayout.setErrorEnabled(true);
            mBinding.edtConfigNameLayout.setError(getString(R.string.enter_config_name));
            mBinding.edtConfigName.requestFocus();
            sshConfigFocus = false;
        }
        configViewUtil.showErrors(sshConfigFocus);




    }


}
