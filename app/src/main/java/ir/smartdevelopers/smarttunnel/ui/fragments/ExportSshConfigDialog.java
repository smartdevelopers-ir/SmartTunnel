package ir.smartdevelopers.smarttunnel.ui.fragments;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.google.gson.Gson;

import java.io.File;
import java.io.OutputStream;

import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.databinding.DialogSshConfigExportBinding;
import ir.smartdevelopers.smarttunnel.ui.interfaces.OnCompleteListener;
import ir.smartdevelopers.smarttunnel.ui.models.SSHConfig;
import ir.smartdevelopers.smarttunnel.ui.models.SSHProxy;
import ir.smartdevelopers.smarttunnel.ui.utils.ConfigsUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.PrefsUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.Util;
import ir.smartdevelopers.smarttunnel.ui.viewModels.AddSSHConfigViewModel;

public class ExportSshConfigDialog extends Fragment {
    private DialogSshConfigExportBinding mBinding;
    private AddSSHConfigViewModel mViewModel;

    private ActivityResultLauncher<Uri> directoryPicker = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), new ActivityResultCallback<Uri>() {
        @Override
        public void onActivityResult(Uri result) {
            if (result != null) {
                    requireContext().getContentResolver().takePersistableUriPermission(result,
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    PrefsUtil.saveConfigFilesDirectoryPath(requireContext(), result.toString());
                    exportConfig();

            }
        }
    });

    public static ExportSshConfigDialog getInstance() {
        return new ExportSshConfigDialog();
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = DialogSshConfigExportBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewModel = new ViewModelProvider(requireActivity()).get(AddSSHConfigViewModel.class);
        initViews();
    }

    private void initViews() {
        mBinding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                exportConfig();
            }
            return true;
        });
    }

    private void exportConfig() {
        CharSequence fileName = mBinding.edtName1.getText();
        if (fileName == null || fileName.length() == 0) {
            mBinding.edtNameLayout.setError(getString(R.string.enter_file_name));
            mBinding.edtName1.requestFocus();
            return;
        }
        String configDirectoryPath = PrefsUtil.getConfigFilesDirectoryPath(requireContext());
        if (configDirectoryPath == null) {
            openSelectDirectory();
            return;
        }
        SSHConfig config = generateConfig();
        config.setServerAddressLocked(mBinding.chbLockServerAddress.isChecked());
        config.setServerPortLocked(mBinding.chbLockServerPort.isChecked());
        config.setUsernameLocked(mBinding.chbLockUsername.isChecked());
        config.setPasswordLocked(mBinding.chbLockPassword.isChecked());
        config.setPrivateKeyLocked(mBinding.chbLockPrivateKey.isChecked());
        config.setConnectionModeLocked(mBinding.chbLockConnectionType.isChecked());
        if (mBinding.edtNote.getText() != null) {
            config.setNote(mBinding.edtNote.getText().toString());
        }
        String exportConfigJson = new Gson().toJson(config);
        String fName = fileName + ".st" ;
        Uri directorUri = Uri.parse(configDirectoryPath);
        DocumentFile configDirectory = DocumentFile.fromTreeUri(requireContext(),directorUri);
        if (configDirectory == null || !configDirectory.exists()){
            PrefsUtil.saveConfigFilesDirectoryPath(requireContext(),null);
            openSelectDirectory();
            return;
        }
        DocumentFile configFile = configDirectory.findFile(fName);
        boolean exists = configFile !=null && configFile.exists();
        if (configFile == null){
            configFile = configDirectory.createFile("application/st",fName);
        }
        if (configFile == null){
            Toast.makeText(requireContext(), R.string.error_in_export_file, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            OutputStream configOutStream = requireContext().getContentResolver().openOutputStream(configFile.getUri());
            if (exists) {

                AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
                builder.setMessage(R.string.file_exists_message)
                        .setTitle(R.string.file_exists_title)
                        .setPositiveButton(R.string.replace, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                export(exportConfigJson, config.getType(), configOutStream);
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                builder.create().show();
            } else {
                export(exportConfigJson, config.getType(), configOutStream);
            }
        }catch (Exception e){

        }


    }

    private void openSelectDirectory() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(R.string.directory_to_save)
                .setMessage(R.string.directory_to_save_message)
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        directoryPicker.launch(null);

                    }
                });
        builder.create().show();
    }

    private void export(String exportConfigJson, String configType, OutputStream outputStream) {
        ConfigsUtil.exportConfig(exportConfigJson, configType, outputStream, new OnCompleteListener<Boolean>() {
            @Override
            public void onComplete(Boolean success) {
                if (success) {
                    Toast.makeText(requireActivity(), getString(R.string.config_saved), Toast.LENGTH_SHORT).show();
                    getParentFragmentManager().popBackStack();
                } else {
                    Toast.makeText(requireActivity(), R.string.error_in_export_file, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * Before calling this, call checkConfig
     */
    private SSHConfig generateConfig() {
        SSHConfig.Builder builder = mViewModel.getSSHConfigBuilder();
        if (builder.getConnectionType() == SSHConfig.CONNECTION_TYPE_SSH_PROXY) {
            SSHProxy jumper = new SSHProxy(mViewModel.getJumperConfigBuilder().build());
            builder.setJumper(jumper);
        }
        return builder.build();
    }
}
