package ir.smartdevelopers.smarttunnel.ui.models;

import android.os.ParcelFileDescriptor;

import java.net.Socket;
import java.util.Locale;

import engine.Key;
import ir.smartdevelopers.smarttunnel.MyVpnService;
import ir.smartdevelopers.smarttunnel.channels.JschRemoteConnection;
import ir.smartdevelopers.smarttunnel.exceptions.RemoteConnectionException;
import ir.smartdevelopers.smarttunnel.ui.exceptions.ConfigException;

public class GoTun2SocksConfig extends Config{
    public static final String CONFIG_TYPE = "squid";
    private HttpProxy mProxy;
    private String mServerAddress;
    private int mServerPort;
    private String mUsername;
    private String mPassword;
    private boolean mUsePrivateKey;
    private PrivateKey mPrivateKey;
    private int mUDPGWPort;
    private int mLocalPort;

    private JschRemoteConnection mRemoteConnection;
    private boolean mCanceled = false;
    public GoTun2SocksConfig(String name, String id, HttpProxy proxy, String serverAddress,
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
                    false, false, false);
            mRemoteConnection.setPrivateKey(mPrivateKey);
            mRemoteConnection.connect();

            if (mRemoteConnection.isPortInUse(1080)){
                mLocalPort = 1080;
            }else {
                 mLocalPort = mRemoteConnection.startLocalPortForwarding("127.0.0.1",1080,
                        mProxy.getAddress(),mProxy.getPort());
            }


        } catch (RemoteConnectionException e) {
            throw new ConfigException(e);
        }
    }

    @Override
    public void setFileDescriptor(ParcelFileDescriptor fileDescriptor) {
        super.setFileDescriptor(fileDescriptor);
        Key key = new Key();
        key.setDevice("fd://" + mFileDescriptor.getFd());
        key.setProxy(String.format(Locale.ENGLISH,"socks5://%s:%d","127.0.0.1",mLocalPort));
        key.setMark(0);
        key.setMTU(0);
        key.setInterface("");
        key.setLogLevel("debug");
        key.setRestAPI("");
        key.setTCPSendBufferSize("");
        key.setTCPReceiveBufferSize("");
        key.setTCPModerateReceiveBuffer(false);
        engine.Engine.insert(key);
        engine.Engine.start();
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

        if (mRemoteConnection != null) {
            try {
                mRemoteConnection.stopLocalPortForwarding("127.0.0.1",1080);
            } catch (RemoteConnectionException e) {
                e.printStackTrace();
            }
            mRemoteConnection.disconnect();
        }
    }

    @Override
    public boolean isCanceled() {
        return mCanceled;
    }



}
