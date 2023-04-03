package ir.smartdevelopers.smarttunnel.ui.models;

public class SSHProxy extends Proxy{

    public static final String LOCAL_ADDRESS = "127.0.0.1";
    public static final int LOCAL_PORT = 1090;
    private SSHConfig mSSHConfig;
    public SSHProxy( SSHConfig sshConfig) {
        super(LOCAL_ADDRESS, LOCAL_PORT);
        mSSHConfig = sshConfig;
    }

    public SSHConfig getSSHConfig() {
        return mSSHConfig;
    }

    public SSHProxy setSSHConfig(SSHConfig SSHConfig) {
        mSSHConfig = SSHConfig;
        return this;
    }
}
