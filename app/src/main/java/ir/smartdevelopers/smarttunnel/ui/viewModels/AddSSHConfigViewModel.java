package ir.smartdevelopers.smarttunnel.ui.viewModels;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import ir.smartdevelopers.smarttunnel.ui.models.SSHConfig;

public class AddSSHConfigViewModel extends ViewModel {

    private MutableLiveData<SSHConfig.Builder> mJumperConfigLiveData;

    public AddSSHConfigViewModel() {

        mJumperConfigLiveData = new MutableLiveData<>();
        mJumperConfigLiveData.setValue(new SSHConfig.Builder(SSHConfig.MODE_PROXY));
    }

    public SSHConfig.Builder getJumperConfigBuilder(){
        return mJumperConfigLiveData.getValue();
    }
    public void setJumperConfigBuilder(SSHConfig.Builder config){
        mJumperConfigLiveData.setValue(config);
    }
    public void clearJumper(){
        mJumperConfigLiveData.setValue(new SSHConfig.Builder(SSHConfig.MODE_PROXY));
    }
}
