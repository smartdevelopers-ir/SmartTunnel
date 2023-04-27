package ir.smartdevelopers.smarttunnel.ui.viewModels;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import ir.smartdevelopers.smarttunnel.ui.models.SSHConfig;

public class SshConfigVieModel extends ViewModel {
    private MutableLiveData<SSHConfig.Builder> mSSHConfigLiveData;

    public SshConfigVieModel() {
        mSSHConfigLiveData = new MutableLiveData<>();
        mSSHConfigLiveData.setValue(new SSHConfig.Builder(SSHConfig.MODE_MAIN_CONNECTION));

    }
    public SSHConfig.Builder getSSHConfigBuilder(){
        return mSSHConfigLiveData.getValue();
    }
    public void setSSHConfig(SSHConfig.Builder config){
        mSSHConfigLiveData.setValue(config);
    }
}
