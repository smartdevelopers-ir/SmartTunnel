package ir.smartdevelopers.smarttunnel.ui.models;

import java.net.Socket;

import engine.Key;
import ir.smartdevelopers.smarttunnel.channels.JschRemoteConnection;
import ir.smartdevelopers.smarttunnel.exceptions.RemoteConnectionException;
import ir.smartdevelopers.smarttunnel.managers.PacketManager;
import ir.smartdevelopers.smarttunnel.managers.Tun2SocksChannelManager;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.ui.exceptions.ConfigException;

public class Tun2SocksConfig extends Config{
    public static final String CONFIG_TYPE = "squid";
    private HttpProxy mProxy;
    private String mServerAddress;
    private int mServerPort;
    private String mUsername;
    private String mPassword;
    private boolean mUsePrivateKey;
    private PrivateKey mPrivateKey;
    private int mUDPGWPort;
    private PacketManager mPacketManager;
    private JschRemoteConnection mRemoteConnection;
    private boolean mCanceled = false;
    public Tun2SocksConfig(String name, String id, HttpProxy proxy, String serverAddress,
                           int serverPort, String username, String password,
                           boolean usePrivateKey, PrivateKey privateKey, int UDPGWPort) {
        super(name, id, CONFIG_TYPE);
        mProxy = proxy;
        mServerAddress = serverAddress;
        mServerPort = serverPort;
        mUsername = username;
        mPassword = password;
        mUsePrivateKey = usePrivateKey;
        mPrivateKey = privateKey;
        mUDPGWPort = UDPGWPort;
    }

    @Override
    public void connect() throws ConfigException {
        try {
            mRemoteConnection = new JschRemoteConnection(mServerAddress,mServerPort,mUsername,mPassword);
            mRemoteConnection.setPrivateKey(mPrivateKey);
            mRemoteConnection.connect();
            engine.Key key = new Key();
//            Tun2SocksChannelManager channelManager = new Tun2SocksChannelManager(mRemoteConnection,
//                    mProxy,mUDPGWPort);
//            ServerPacketListener packetListener = new ServerPacketListener(this);
//            mPacketManager = new PacketManager(packetListener,channelManager);
        } catch (RemoteConnectionException e) {
            throw new ConfigException(e);
        }
    }

    @Override
    public Socket getMainSocket() {
        return mRemoteConnection.getMainSocket();
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
        if (mRemoteConnection != null) {
            mRemoteConnection.disconnect();
        }
    }

    @Override
    public boolean isCanceled() {
        return mCanceled;
    }

    @Override
    public void sendPacketToRemoteServer(byte[] packet) {
        mPacketManager.sendToRemoteServer(packet);
    }
    private static class ServerPacketListener implements PacketManager.ServerPacketListener {

        private final Tun2SocksConfig mConfig;

        public ServerPacketListener(Tun2SocksConfig config) {
            mConfig = config;
        }

        @Override
        public synchronized void onPacketFromServer(Packet packet) {

            if (mConfig.mOnPacketFromServerListener != null) {
                mConfig.mOnPacketFromServerListener.onPacketFromServer(packet.getPacketBytes());
            }

        }
    }
}
