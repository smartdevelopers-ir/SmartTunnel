package ir.smartdevelopers.smarttunnel.ui.models;

import android.os.ParcelFileDescriptor;
import android.text.TextUtils;

import com.jcraft.jsch.HostKeyRepository;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.UUID;

import ir.smartdevelopers.smarttunnel.MyVpnService;
import ir.smartdevelopers.smarttunnel.channels.JschRemoteConnection;
import ir.smartdevelopers.smarttunnel.channels.RemoteConnection;
import ir.smartdevelopers.smarttunnel.exceptions.RemoteConnectionException;
import ir.smartdevelopers.smarttunnel.managers.ChannelManager;
import ir.smartdevelopers.smarttunnel.managers.PacketManager;
import ir.smartdevelopers.smarttunnel.managers.SshChannelManager;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.ui.classes.AcceptAllHostRepo;
import ir.smartdevelopers.smarttunnel.ui.classes.LocalReader;
import ir.smartdevelopers.smarttunnel.ui.exceptions.ConfigException;

public class SSHConfig extends Config {
    /**
     * This is SSH config type
     */
    public final static String CONFIG_TYPE = "SSH";
    public static int CONNECTION_TYPE_DIRECT = 1001;
    public static int CONNECTION_TYPE_WEBSOCKET = 1002;
    public static int CONNECTION_TYPE_SSH_PROXY = 1003;
    public static int CONNECTION_TYPE_SSL_TLS = 1004;
    public static int DEFAULT_UDPGW_PORT = 7300;
    public static int MODE_MAIN_CONNECTION = 300;
    public static int MODE_PROXY = 301;
    /**
     * determine this config is proxy or main connection
     */
    private int mConfigMode;
    private String mServerAddress;
    private int mServerPort;
    private String mUsername;
    private String mPassword;
    private boolean mUsePrivateKey;
    private PrivateKey mPrivateKey;
    private int mUDPGWPort;
    private SSHProxy mJumper;
    private int mConnectionType;
    private String mPayload;
    private String mServerNameIndicator;
    private boolean serverAddressLocked;
    private boolean serverPortLocked;
    private boolean usernameLocked;
    private boolean passwordLocked;
    private boolean privateKeyLocked;
    private boolean connectionModeLocked;
    private boolean preferIPv6;
    private transient HostKeyRepository mHostKeyRepo;
    private transient RemoteConnection mRemoteConnection;
    private transient PacketManager mPacketManager;
    private transient boolean mCanceled;
    private transient FileInputStream mLocalIn;
    private transient FileOutputStream mLocalOut;
    private transient LocalReader mLocalReader;


    private SSHConfig(String name, String id, String type) {
        super(name, id, type);
        mHostKeyRepo = new AcceptAllHostRepo();
    }

    public SSHConfig(String name, String id, String type, int configMode, String serverAddress,
                     int serverPort, String username, String password,
                     boolean usePrivateKey, PrivateKey privateKey, int UDPGWPort,
                     SSHProxy jumper, int connectionType, String payload,
                     String serverNameIndicator) {
        super(name, id, type);
        mConfigMode = configMode;
        mServerAddress = serverAddress;
        mServerPort = serverPort;
        mUsername = username;
        mPassword = password;
        mUsePrivateKey = usePrivateKey;
        mPrivateKey = privateKey;
        mUDPGWPort = UDPGWPort;
        mJumper = jumper;
        mConnectionType = connectionType;
        mPayload = payload;
        mServerNameIndicator = serverNameIndicator;
        mHostKeyRepo = new AcceptAllHostRepo();
    }

    @Override
    public void connect() throws ConfigException {

        try {

            PrivateKey privateKey = mPrivateKey;
            if (mUsePrivateKey && mPrivateKey != null) {
                privateKey = null;
            }
            String proxifiedAddress = mServerAddress;
            int proxifiedPort = mServerPort;
            if (getProxy() == null) {
                if (mJumper != null) {
                    connectSSHProxy(mJumper, mServerAddress, mServerPort);
                    proxifiedAddress = mJumper.getAddress();
                    proxifiedPort = mJumper.getPort();
                }
            } else {
                if (getProxy() instanceof SSHProxy) {
                    if (mJumper != null) {
                        // in this case jumper have a server address for its sshconfig
                        // and we must set a local port forwarding to that address
                        // then jumper must connect to getProxy().getAddress() and
                        // getProxy().getPort() instead
                        connectSSHProxy((SSHProxy) getProxy(), mJumper.getSSHConfig().getServerAddress(),
                                mJumper.getSSHConfig().getServerPort());
                        mJumper.getSSHConfig().setServerAddress(getProxy().getAddress());
                        mJumper.getSSHConfig().setServerPort(getProxy().getPort());
                        connectSSHProxy(mJumper, mServerAddress, mServerPort);
                        proxifiedAddress = mJumper.getAddress();
                        proxifiedPort = mJumper.getPort();
                    } else {
                        connectSSHProxy((SSHProxy) getProxy(), mServerAddress, mServerPort);
                        proxifiedAddress = getProxy().getAddress();
                        proxifiedPort = getProxy().getPort();
                    }
                }
            }
            JschRemoteConnection connection = new JschRemoteConnection(proxifiedAddress,proxifiedPort,mUsername,mPassword,
                    isServerAddressLocked(), isServerPortLocked(), isUsernameLocked(),"8.8.8.8",false);
            connection.setPrivateKey(privateKey);
            mRemoteConnection = connection;
            if (getProxy() instanceof HttpProxy) {
                mRemoteConnection.setProxy(getProxy());
            }
            mRemoteConnection.connect();
            if (mConfigMode == MODE_PROXY) {
                return;
            }
            ServerPacketListener serverPacketListener = new ServerPacketListener(this);
            ChannelManager channelManager = new SshChannelManager(mRemoteConnection,mUDPGWPort);
            mPacketManager = new PacketManager(serverPacketListener,channelManager);



        } catch (RemoteConnectionException e) {
            throw new ConfigException(e);
        }
    }

    /**
     * we connect to proxy ssh server first, then we set local port forwarding
     * to destAddress and destPort
     */
    private void connectSSHProxy(SSHProxy proxy, String destAddress, int destPort) throws ConfigException {
        proxy.getSSHConfig().connect();
        RemoteConnection connection = proxy.getSSHConfig().mRemoteConnection;
        int localPort;
        try {
            localPort = connection.startLocalPortForwarding(proxy.getAddress(), proxy.getPort(), destAddress, destPort);
        } catch (RemoteConnectionException e) {
            throw new ConfigException(e);
        }
        proxy.setPort(localPort);
    }

    @Override
    public void setFileDescriptor(ParcelFileDescriptor fileDescriptor) {
        super.setFileDescriptor(fileDescriptor);
        mLocalIn = new FileInputStream(fileDescriptor.getFileDescriptor());
        mLocalOut = new FileOutputStream(fileDescriptor.getFileDescriptor());
        mLocalReader = new LocalReader(mLocalIn,this,mPacketManager);
        mLocalReader.start();
    }

    @Override
    public ParcelFileDescriptor getFileDescriptor() {
        return null;
    }

    @Override
    public Socket getMainSocket() {

        return mRemoteConnection.getMainSocket();
    }

    @Override
    public void retry() {
        if (mVpnService != null){
            MyVpnService.reconnect(mVpnService.getApplicationContext());
        }
    }

    @Override
    public void cancel() {
        mCanceled = true;
        if (mPacketManager != null) {
            mPacketManager.destroy();
        }
        if (mRemoteConnection != null) {
            mRemoteConnection.disconnect();
        }
        if (mJumper != null) {
            mJumper.getSSHConfig().cancel();
        }
        if (mLocalReader != null){
            mLocalReader.interrupt();
        }
    }


    public RemoteConnection getRemoteConnection() {
        return mRemoteConnection;
    }

    public String getServerAddress() {
        return mServerAddress;
    }

    public void setServerAddress(String serverAddress) {
        mServerAddress = serverAddress;
    }

    public int getServerPort() {
        return mServerPort;
    }

    public void setServerPort(int serverPort) {
        mServerPort = serverPort;
    }

    public String getUsername() {
        return mUsername;
    }

    public void setUsername(String username) {
        mUsername = username;
    }

    public String getPassword() {
        return mPassword;
    }

    public void setPassword(String password) {
        mPassword = password;
    }

    public boolean isUsePrivateKey() {
        return mUsePrivateKey;
    }

    public void setUsePrivateKey(boolean usePrivateKey) {
        mUsePrivateKey = usePrivateKey;
    }

    public PrivateKey getPrivateKey() {
        return mPrivateKey;
    }

    public void setPrivateKey(PrivateKey privateKey) {
        mPrivateKey = privateKey;
    }

    public int getUDPGWPort() {
        return mUDPGWPort;
    }

    public void setUDPGWPort(int UDPGWPort) {
        mUDPGWPort = UDPGWPort;
    }

    public SSHProxy getJumper() {
        return mJumper;
    }

    public void setJumper(SSHProxy jumper) {
        mJumper = jumper;
    }

    public int getConnectionType() {
        return mConnectionType;
    }

    public void setConnectionType(int connectionType) {
        mConnectionType = connectionType;
    }

    public String getPayload() {
        return mPayload;
    }

    public void setPayload(String payload) {
        mPayload = payload;
    }

    public String getServerNameIndicator() {
        return mServerNameIndicator;
    }

    public void setServerNameIndicator(String serverNameIndicator) {
        mServerNameIndicator = serverNameIndicator;
    }

    public HostKeyRepository getHostKeyRepo() {
        return mHostKeyRepo;
    }

    public void setHostKeyRepo(HostKeyRepository hostKeyRepo) {
        mHostKeyRepo = hostKeyRepo;
    }

    public boolean isCanceled() {
        return mCanceled;
    }

    public boolean isServerAddressLocked() {
        return serverAddressLocked;
    }

    public void setServerAddressLocked(boolean serverAddressLocked) {
        this.serverAddressLocked = serverAddressLocked;
    }

    public boolean isServerPortLocked() {
        return serverPortLocked;
    }

    public void setServerPortLocked(boolean serverPortLocked) {
        this.serverPortLocked = serverPortLocked;
    }

    public boolean isUsernameLocked() {
        return usernameLocked;
    }

    public void setUsernameLocked(boolean usernameLocked) {
        this.usernameLocked = usernameLocked;
    }

    public boolean isPasswordLocked() {
        return passwordLocked;
    }

    public void setPasswordLocked(boolean passwordLocked) {
        this.passwordLocked = passwordLocked;
    }

    public boolean isPrivateKeyLocked() {
        return privateKeyLocked;
    }

    public void setPrivateKeyLocked(boolean privateKeyLocked) {
        this.privateKeyLocked = privateKeyLocked;
    }

    public boolean isConnectionModeLocked() {
        return connectionModeLocked;
    }

    public void setConnectionModeLocked(boolean connectionModeLocked) {
        this.connectionModeLocked = connectionModeLocked;

    }

    public boolean isPreferIPv6() {
        return preferIPv6;
    }

    public SSHConfig setPreferIPv6(boolean preferIPv6) {
        this.preferIPv6 = preferIPv6;
        return this;
    }


    private static class ServerPacketListener implements PacketManager.ServerPacketListener {

        private final SSHConfig mConfig;

        public ServerPacketListener(SSHConfig config) {
            mConfig = config;
        }

        @Override
        public synchronized void onPacketFromServer(Packet packet) {

            if (mConfig.mLocalOut!=null){
                try {
                    mConfig.mLocalOut.write(packet.getPacketBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    public Builder toBuilder(){
        Builder builder = new Builder(getName(),mConfigMode,getServerAddress(),getServerPort(),
                getUsername(),getPassword());
        builder.setJumper(mJumper)
                .setPrivateKey(mPrivateKey)
                .setUsePrivateKey(mUsePrivateKey)
                .setId(getId())
                .setPayload(mPayload)
                .setConnectionType(mConnectionType)
                .setUDPGWPort(mUDPGWPort)
                .setServerNameIndicator(mServerNameIndicator)
                .setServerAddressLocked(serverAddressLocked)
                .setServerPortLocked(serverPortLocked)
                .setUsernameLocked(usernameLocked)
                .setPasswordLocked(passwordLocked)
                .setPrivateKeyLocked(privateKeyLocked)
                .setConnectionModeLocked(connectionModeLocked)
                .setPreferIPv6(preferIPv6);
        return builder;
    }
    public static class Builder {
        private String id;
        private String name;
        private int mConfigMode;
        private String mServerAddress;
        private int mServerPort;
        private String mUsername;
        private String mPassword;
        private boolean mUsePrivateKey;
        private PrivateKey mPrivateKey;
        private int mUDPGWPort;
        private SSHProxy mJumper;
        private int mConnectionType;
        private String mPayload;
        private String mServerNameIndicator;
        private boolean serverAddressLocked;
        private boolean serverPortLocked;
        private boolean usernameLocked;
        private boolean passwordLocked;
        private boolean privateKeyLocked;
        private boolean connectionModeLocked;
        private boolean preferIPv6;


        public Builder(int configMode) {
            mConfigMode = configMode;
        }

        public Builder(String name, int configMode, String serverAddress, int serverPort, String username,
                       String password) {
            this.name = name;
            mConfigMode = configMode;
            mServerAddress = serverAddress;
            mServerPort = serverPort;
            mUsername = username;
            mPassword = password;
        }

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setConfigMode(int configMode) {
            mConfigMode = configMode;
            return this;
        }

        public Builder setServerAddress(String serverAddress) {
            mServerAddress = serverAddress;
            return this;
        }

        public Builder setServerPort(int serverPort) {
            mServerPort = serverPort;
            return this;
        }

        public Builder setUsername(String username) {
            mUsername = username;
            return this;
        }

        public Builder setPassword(String password) {
            mPassword = password;
            return this;
        }

        public Builder setUsePrivateKey(boolean usePrivateKey) {
            mUsePrivateKey = usePrivateKey;
            return this;
        }

        public Builder setPrivateKey(PrivateKey privateKey) {
            mPrivateKey = privateKey;
            return this;
        }

        public Builder setUDPGWPort(int UDPGWPort) {
            mUDPGWPort = UDPGWPort;
            return this;
        }

        public Builder setJumper(SSHProxy jumper) {
            mJumper = jumper;
            return this;
        }

        public Builder setConnectionType(int connectionType) {
            mConnectionType = connectionType;
            return this;
        }

        public Builder setPayload(String payload) {
            mPayload = payload;
            return this;
        }

        public Builder setServerNameIndicator(String serverNameIndicator) {
            mServerNameIndicator = serverNameIndicator;
            return this;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getConfigMode() {
            return mConfigMode;
        }

        public String getServerAddress() {
            return mServerAddress;
        }

        public int getServerPort() {
            return mServerPort;
        }

        public String getUsername() {
            return mUsername;
        }

        public String getPassword() {
            return mPassword;
        }

        public boolean isUsePrivateKey() {
            return mUsePrivateKey;
        }

        public PrivateKey getPrivateKey() {
            return mPrivateKey;
        }

        public int getUDPGWPort() {
            return mUDPGWPort;
        }

        public SSHProxy getJumper() {
            return mJumper;
        }

        public int getConnectionType() {
            return mConnectionType;
        }

        public String getPayload() {
            return mPayload;
        }

        public String getServerNameIndicator() {
            return mServerNameIndicator;
        }

        public SSHConfig build() {
            if (TextUtils.isEmpty(id)) {
                id = UUID.randomUUID().toString();
            }
            if (mConnectionType == 0) {
                mConnectionType = CONNECTION_TYPE_DIRECT;
            }
            if (mUDPGWPort == 0) {
                mUDPGWPort = DEFAULT_UDPGW_PORT;
            }

            SSHConfig config = new SSHConfig(name, id, CONFIG_TYPE,
                    mConfigMode, mServerAddress, mServerPort, mUsername,
                    mPassword, mUsePrivateKey, mPrivateKey, mUDPGWPort,
                    mJumper, mConnectionType, mPayload, mServerNameIndicator);
            config.passwordLocked = passwordLocked;
            config.usernameLocked = usernameLocked;
            config.serverAddressLocked = serverAddressLocked;
            config.serverPortLocked = serverPortLocked;
            config.privateKeyLocked = privateKeyLocked;
            config.connectionModeLocked = connectionModeLocked;
            config.setPreferIPv6(preferIPv6);

            return config;

        }


        public boolean isServerAddressLocked() {
            return serverAddressLocked;
        }

        public Builder setServerAddressLocked(boolean serverAddressLocked) {
            this.serverAddressLocked = serverAddressLocked;
            return this;
        }

        public boolean isServerPortLocked() {
            return serverPortLocked;
        }

        public Builder setServerPortLocked(boolean serverPortLocked) {
            this.serverPortLocked = serverPortLocked;
            return this;
        }

        public boolean isUsernameLocked() {
            return usernameLocked;
        }

        public Builder setUsernameLocked(boolean usernameLocked) {
            this.usernameLocked = usernameLocked;
            return this;
        }

        public boolean isPasswordLocked() {
            return passwordLocked;
        }

        public Builder setPasswordLocked(boolean passwordLocked) {
            this.passwordLocked = passwordLocked;
            return this;
        }

        public boolean isPrivateKeyLocked() {
            return privateKeyLocked;
        }

        public Builder setPrivateKeyLocked(boolean privateKeyLocked) {
            this.privateKeyLocked = privateKeyLocked;
            return this;
        }

        public boolean isConnectionModeLocked() {
            return connectionModeLocked;
        }

        public Builder setConnectionModeLocked(boolean connectionModeLocked) {
            this.connectionModeLocked = connectionModeLocked;
            return this;
        }

        public boolean isPreferIPv6() {
            return preferIPv6;
        }

        public Builder setPreferIPv6(boolean preferIPv6) {
            this.preferIPv6 = preferIPv6;
            return this;
        }
    }

}
