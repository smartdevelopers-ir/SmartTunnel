package ir.smartdevelopers.smarttunnel.ui.viewModels;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import de.blinkt.openvpn.VpnProfile;
import ir.smartdevelopers.smarttunnel.ui.models.OpenVpnConfig;
import ir.smartdevelopers.smarttunnel.ui.models.SSHConfig;

public class AddOpenVPNConfigViewModel extends ViewModel {
    private MutableLiveData<VpnProfile> mOpenVpnConfigLiveData;

    public AddOpenVPNConfigViewModel() {
        mOpenVpnConfigLiveData = new MutableLiveData<>();

    }
    public VpnProfile getOpenVpnProfile(){
        return mOpenVpnConfigLiveData.getValue();
    }
    public void setOpenVpnProfile(VpnProfile profile){
        mOpenVpnConfigLiveData.setValue(profile);
    }

    public OpenVpnConfig generateConfig(SSHConfig.Builder sshConfigBuilder, String id){
        OpenVpnConfig.Builder builder = new OpenVpnConfig.Builder();
        builder.setName(sshConfigBuilder.getName())
                        .setId(id)
                .setProfile(getOpenVpnProfile())
                .setServerAddress(sshConfigBuilder.getServerAddress())
                .setServerPort(sshConfigBuilder.getServerPort())
                .setUsername(sshConfigBuilder.getUsername())
                .setPassword(sshConfigBuilder.getPassword())
                .setPrivateKey(sshConfigBuilder.getPrivateKey())
                .setUsePrivateKey(sshConfigBuilder.isUsePrivateKey())
                .setServerAddressLocked(sshConfigBuilder.isServerAddressLocked())
                .setServerPortLocked(sshConfigBuilder.isServerPortLocked())
                .setUsernameLocked(sshConfigBuilder.isUsernameLocked())
                .setPasswordLocked(sshConfigBuilder.isPasswordLocked())
                .setPrivateKeyLocked(sshConfigBuilder.isPrivateKeyLocked())
                .setPreferIPv6(sshConfigBuilder.isPreferIPv6());

        return builder.build();
    }
}
