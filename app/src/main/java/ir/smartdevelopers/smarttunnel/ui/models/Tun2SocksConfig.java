package ir.smartdevelopers.smarttunnel.ui.models;

import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;

import ir.smartdevelopers.smarttunnel.MyVpnService;
import ir.smartdevelopers.smarttunnel.channels.JschRemoteConnection;
import ir.smartdevelopers.smarttunnel.exceptions.RemoteConnectionException;
import ir.smartdevelopers.smarttunnel.managers.PacketManager;
import ir.smartdevelopers.smarttunnel.managers.Tun2SocksChannelManager;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.ui.classes.LocalReader;
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
    private transient FileOutputStream mLocalOut;
    private transient LocalReader mLocalReader;
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
            mRemoteConnection = new JschRemoteConnection(mServerAddress,mServerPort,mUsername,mPassword,
                    false, false, false,"8.8.8.8",false);
            mRemoteConnection.setPrivateKey(mPrivateKey);
            mRemoteConnection.connect();
            if (mRemoteConnection.isPortInUse(1080)){
                mRemoteConnection.stopLocalPortForwarding("127.0.0.1",1080);
            }
             mRemoteConnection.startLocalPortForwarding("127.0.0.1",1080,
                    mProxy.getAddress(),mProxy.getPort());

            Tun2SocksChannelManager channelManager = new Tun2SocksChannelManager(mRemoteConnection,mUDPGWPort);
            ServerPacketListener packetListener = new ServerPacketListener(this);
            mPacketManager = new PacketManager(packetListener,channelManager);
        } catch (RemoteConnectionException e) {
            throw new ConfigException(e);
        }
    }
    @Override
    public void setFileDescriptor(ParcelFileDescriptor fileDescriptor) {
        super.setFileDescriptor(fileDescriptor);
        FileInputStream localIn = new FileInputStream(fileDescriptor.getFileDescriptor());
        mLocalOut = new FileOutputStream(fileDescriptor.getFileDescriptor());
        mLocalReader = new LocalReader(localIn,this,mPacketManager);
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
        if (mLocalReader != null){
            mLocalReader.interrupt();
        }
    }

    @Override
    public boolean isCanceled() {
        return mCanceled;
    }


    private static class ServerPacketListener implements PacketManager.ServerPacketListener {

        private final Tun2SocksConfig mConfig;

        public ServerPacketListener(Tun2SocksConfig config) {
            mConfig = config;
        }

        @Override
        public synchronized void onPacketFromServer(Packet packet) {

            if (mConfig.mLocalOut != null) {
                try {
                    mConfig.mLocalOut.write(packet.getPacketBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }
}
