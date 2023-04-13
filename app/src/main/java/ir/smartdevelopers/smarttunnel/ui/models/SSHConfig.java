package ir.smartdevelopers.smarttunnel.ui.models;

import android.text.TextUtils;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.ProxyHTTP;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.UUID;

import ir.smartdevelopers.smarttunnel.HostKeyRepo;
import ir.smartdevelopers.smarttunnel.managers.PacketManager;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.ui.exceptions.ConfigException;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;
import ir.smartdevelopers.smarttunnel.utils.Logger;

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
    private HostKeyRepo mHostKeyRepo;
    private transient JSch mJSch;
    private transient Session mSession;
    private transient PacketManager mPacketManager;
    private transient boolean mCanceled;

    private SSHConfig(String name, String id, String type) {
        super(name, id, type);
    }

    public SSHConfig(String name, String id, String type, int configMode, String serverAddress,
                     int serverPort, String username, String password,
                     boolean usePrivateKey, PrivateKey privateKey, int UDPGWPort,
                     SSHProxy jumper, int connectionType, String payload,
                     String serverNameIndicator, HostKeyRepo hostKeyRepo) {
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
        mHostKeyRepo = hostKeyRepo;
    }

    @Override
    public void connect() throws ConfigException {

        try {
            mJSch = new JSch();
            mJSch.setHostKeyRepository(mHostKeyRepo);
            if (mUsePrivateKey && mPrivateKey != null) {
                KeyPair keyPair = KeyPair.load(mJSch, mPrivateKey.key.getBytes(), null);
                ByteArrayOutputStream prKeyStream = new ByteArrayOutputStream();
                keyPair.writePrivateKey(prKeyStream);
                byte[] prKey = prKeyStream.toByteArray();
                mJSch.addIdentity(keyPair.getPublicKeyComment(), prKey, keyPair.getPublicKeyBlob(), null);
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
            mSession = mJSch.getSession(mUsername, proxifiedAddress, proxifiedPort);
            if (getProxy() instanceof HttpProxy) {
                mSession.setProxy(new ProxyHTTP(getProxy().getAddress(), getProxy().getPort()));
            }
            mSession.setPassword(mPassword);
            mSession.connect(15000);
            if (mConfigMode == MODE_PROXY) {
                return;
            }
            ServerPacketListener serverPacketListener = new ServerPacketListener(this);
            mPacketManager = new PacketManager(mSession, serverPacketListener);


        } catch (JSchException jSchException) {
            throw new ConfigException(jSchException);
        }
    }

    /**
     * we connect to proxy ssh server first, then we set local port forwarding
     * to destAddress and destPort
     */
    private void connectSSHProxy(SSHProxy proxy, String destAddress, int destPort) throws ConfigException, JSchException {
        proxy.getSSHConfig().connect();
        Session session = proxy.getSSHConfig().getSession();
        int localPort = session.setPortForwardingL(proxy.getAddress(), proxy.getPort(), destAddress, destPort);
        proxy.setPort(localPort);
    }

    @Override
    public Socket getMainSocket() {
        if (mSession == null) {
            return null;
        }
        if (!mSession.isConnected()) {
            return null;
        }
        return mSession.getSocket();
    }

    public Session getSession() {
        return mSession;
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

    public HostKeyRepo getHostKeyRepo() {
        return mHostKeyRepo;
    }

    public void setHostKeyRepo(HostKeyRepo hostKeyRepo) {
        mHostKeyRepo = hostKeyRepo;
    }

    @Override
    public void retry() {

    }

    @Override
    public void cancel() {
        mCanceled = true;
        if (mPacketManager != null) {
            mPacketManager.destroy();
        }
        if (mSession != null) {
            mSession.disconnect();
        }
        if (mJumper != null) {
            mJumper.getSSHConfig().cancel();
        }
    }

    public boolean isCanceled() {
        return mCanceled;
    }

    @Override
    public void sendPacketToRemoteServer(byte[] packet) {
        mPacketManager.sendToRemoteServer(packet);
    }


    private static class ServerPacketListener implements PacketManager.ServerPacketListener {

        private final SSHConfig mConfig;

        public ServerPacketListener(SSHConfig config) {
            mConfig = config;
        }

        @Override
        public synchronized void onPacketFromServer(Packet packet) {

            if (mConfig.mOnPacketFromServerListener != null) {
                mConfig.mOnPacketFromServerListener.onPacketFromServer(packet.getPacketBytes());
            }

        }
    }

    public static class Builder {
        private String id;
        private String name;
        private int mConfigMode;
        private String mServerAddress;
        private int mServerPort;
        private String mUsername;
        private String mPassword;
        private HostKeyRepo mHostKeyRepo;
        private boolean mUsePrivateKey;
        private PrivateKey mPrivateKey;
        private int mUDPGWPort;
        private SSHProxy mJumper;
        private int mConnectionType;
        private String mPayload;
        private String mServerNameIndicator;

        public Builder(int configMode) {
            mConfigMode = configMode;
        }

        public Builder(String name, int configMode, String serverAddress, int serverPort, String username,
                       String password, HostKeyRepo hostKeyRepo) {
            this.name = name;
            mConfigMode = configMode;
            mServerAddress = serverAddress;
            mServerPort = serverPort;
            mUsername = username;
            mPassword = password;
            mHostKeyRepo = hostKeyRepo;
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

        public Builder setHostKeyRepo(HostKeyRepo hostKeyRepo) {
            mHostKeyRepo = hostKeyRepo;
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

        public HostKeyRepo getHostKeyRepo() {
            return mHostKeyRepo;
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

            return new SSHConfig(name, id, CONFIG_TYPE,
                    mConfigMode, mServerAddress, mServerPort, mUsername,
                    mPassword, mUsePrivateKey, mPrivateKey, mUDPGWPort,
                    mJumper, mConnectionType, mPayload, mServerNameIndicator,
                    mHostKeyRepo);

        }


    }
}
