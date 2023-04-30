package ir.smartdevelopers.smarttunnel.ui.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;

import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.databinding.SshConfigLayoutBinding;
import ir.smartdevelopers.smarttunnel.ui.classes.SimpleTextWatcher;
import ir.smartdevelopers.smarttunnel.ui.models.PrivateKey;
import ir.smartdevelopers.smarttunnel.ui.models.SSHConfig;

public class SSHConfigViewUtil {
    private SshConfigLayoutBinding mBinding;
    private SSHConfig.Builder mBuilder;
    private Context mContext;

    public SSHConfigViewUtil(Context context,SshConfigLayoutBinding binding, SSHConfig.Builder builder) {
        mBinding = binding;
        mBuilder = builder;
        mContext = context;
    }
    public void initSshConfigViews(){
        mBinding.btnPasteKey.setOnClickListener(v->{
            pasteFromClipBoardAndSaveKey();
        });
        mBinding.chbPrivateKey.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mBinding.btnPasteKey.setEnabled(isChecked && !mBuilder.isPrivateKeyLocked());
            mBinding.btnChooseKey.setEnabled(isChecked && !mBuilder.isPrivateKeyLocked());
            mBuilder.setUsePrivateKey(isChecked);
            if (!isChecked){
                mBuilder.setPrivateKey(null);
                mBinding.txtPrivateKeyNote.setText("");
            }
        });
        if (!mBuilder.isPrivateKeyLocked()){
            if (mBuilder.getPrivateKey() != null){
                setKeyNote(mBuilder.getPrivateKey());
            }
        }else {
            mBinding.chbPrivateKey.setEnabled(false);
            mBinding.btnChooseKey.setEnabled(false);
            mBinding.btnPasteKey.setEnabled(false);
        }
        if (mBuilder.isServerAddressLocked()){
            mBinding.edtServerAddressLayout.setEnabled(false);
            mBinding.edtServerAddress.setEnabled(false);
            mBinding.edtServerAddress.setHint(mBinding.getRoot().getContext().getString(R.string.server_is_locked));
        }else {
            mBinding.edtServerAddress.setText(mBuilder.getServerAddress());
        }
        if (mBuilder.isServerPortLocked()){
            mBinding.edtServerPortLayout.setEnabled(false);
            mBinding.edtServerPort.setEnabled(false);
            mBinding.edtServerPort.setHint(mBinding.getRoot().getContext().getString(R.string.server_port_is_locked));
        }else {
            mBinding.edtServerPort.setText(mBuilder.getServerPort() == 0 ? "" :
                    String.valueOf(mBuilder.getServerPort()));
        }
        if (mBuilder.isUsernameLocked()){
            mBinding.edtUsernameLayout.setEnabled(false);
            mBinding.edtUsername.setEnabled(false);
            mBinding.edtUsername.setHint(mBinding.getRoot().getContext().getString(R.string.username_is_locked));
        }else {
            mBinding.edtUsername.setText(mBuilder.getUsername());
        }
        if (mBuilder.isPasswordLocked()){
            mBinding.edtPasswordLayout.setEnabled(false);
            mBinding.edtPassword.setEnabled(false);
            mBinding.edtPassword.setHint(mBinding.getRoot().getContext().getString(R.string.password_is_locked));
        }else {
            mBinding.edtPassword.setText(mBuilder.getPassword());
        }
        boolean usePrivateKey = mBuilder.isUsePrivateKey();
        mBinding.chbPrivateKey.setChecked(usePrivateKey);
        mBinding.chbPrivateKey.jumpDrawablesToCurrentState();

        if (mBuilder.isPreferIPv6()){
            mBinding.chbPreferIPv6.setChecked(true);
            mBinding.chbPreferIPv6.jumpDrawablesToCurrentState();
        }
        mBinding.chbPreferIPv6.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mBuilder.setPreferIPv6(isChecked);
            }
        });


        mBinding.edtServerAddress.addTextChangedListener(new SimpleTextWatcher(mBinding.edtServerAddress, mBinding.edtServerAddressLayout) {
            @Override
            public void onTextChanged(CharSequence text) {
                mBuilder.setServerAddress(text.toString());
            }
        });
        mBinding.edtServerPort.addTextChangedListener(new SimpleTextWatcher(mBinding.edtServerPort, mBinding.edtServerPortLayout) {
            @Override
            public void onTextChanged(CharSequence text) {
                if (!TextUtils.isEmpty(text)){
                    mBuilder.setServerPort(Integer.parseInt(text.toString()));
                }else {
                    mBuilder.setServerPort(0);
                }
            }
        });
        mBinding.edtUsername.addTextChangedListener(new SimpleTextWatcher(mBinding.edtUsername, mBinding.edtUsernameLayout) {
            @Override
            public void onTextChanged(CharSequence text) {
                mBuilder.setUsername(text.toString());
            }
        });
        mBinding.edtPassword.addTextChangedListener(new SimpleTextWatcher( mBinding.edtPassword,  mBinding.edtPasswordLayout) {
            @Override
            public void onTextChanged(CharSequence text) {
                mBuilder.setPassword(text.toString());
            }
        });
    }
    private void setKeyNote(PrivateKey key){
        if ("text".equals(key.keyType)){
            mBinding.txtPrivateKeyNote.setText(R.string.private_key_text_note);
        }else if ("file".equals(key.keyType)){
            mBinding.txtPrivateKeyNote.setText(mContext.getString(R.string.private_key_file_note,
                    key.name));
        }
    }
    private void pasteFromClipBoardAndSaveKey() {
        String key=null;
        ClipboardManager clipboardManager = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager.getPrimaryClip() != null) {
            ClipData.Item item = clipboardManager.getPrimaryClip().getItemAt(0);
            if (item != null){
                CharSequence data = item.getText();
                if (data != null){
                    key = data.toString();
                }
            }
        }
        if (TextUtils.isEmpty(key)){
            Toast.makeText(mContext, R.string.clipboard_is_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isValidKey(key)){
            Toast.makeText(mContext, R.string.key_is_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        saveKey(key,"text",null);

    }

    private boolean isValidKey(String key) {
        JSch jSch = new JSch();
        boolean valid = false;
        try {
            KeyPair keyPair = KeyPair.load(jSch,key.getBytes(),null);
            if (keyPair != null){
                valid = true;
            }
        } catch (JSchException ignore) {}
        return valid;
    }

    private void saveKey(String key, String keyType,String name) {
        PrivateKey privateKey = new PrivateKey(key, keyType, name);
        mBuilder.setPrivateKey(privateKey);
        setKeyNote(privateKey);
    }

    public boolean showErrors(boolean focus){
        boolean hasError= false;
        View focusView = null;
        if (mBuilder.isUsePrivateKey() && TextUtils.isEmpty(mBuilder.getPassword())){
            mBinding.txtPrivateKeyNote.setTextColor(ContextCompat.getColor(mContext, R.color.colorError));
            mBinding.txtPrivateKeyNote.setText(R.string.private_key_is_empty);
            hasError= true;
        }else if (TextUtils.isEmpty(mBuilder.getPassword())){
            mBinding.edtPasswordLayout.setErrorEnabled(true);
            mBinding.edtPasswordLayout.setError(mContext.getString(R.string.enter_password));
            focusView = mBinding.edtPassword;
            hasError= true;
        }
        if (TextUtils.isEmpty(mBuilder.getUsername())){
            mBinding.edtUsernameLayout.setErrorEnabled(true);
            mBinding.edtUsernameLayout.setError(mContext.getString(R.string.enter_username));
            focusView = mBinding.edtUsername;
            hasError= true;
        }
        if (mBuilder.getServerPort() == 0){
            mBinding.edtServerPortLayout.setErrorEnabled(true);
            mBinding.edtServerPortLayout.setError(mContext.getString(R.string.enter_server_port));
            focusView = mBinding.edtServerPort;
            hasError= true;
        }
        if (TextUtils.isEmpty(mBuilder.getServerAddress())){
            mBinding.edtServerAddressLayout.setErrorEnabled(true);
            mBinding.edtServerAddressLayout.setError(mContext.getString(R.string.enter_server_address));
            focusView = mBinding.edtServerAddress;
            hasError= true;
        }

        if (focusView != null && focus){
            focusView.requestFocus();
        }
        return hasError;
    }
}
