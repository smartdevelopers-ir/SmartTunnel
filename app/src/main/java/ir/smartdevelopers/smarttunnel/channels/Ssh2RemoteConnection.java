package ir.smartdevelopers.smarttunnel.channels;

import android.annotation.SuppressLint;
import android.util.SparseArray;


import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionInfo;
import com.trilead.ssh2.ConnectionMonitor;
import com.trilead.ssh2.DHGexParameters;
import com.trilead.ssh2.DynamicPortForwarder;
import com.trilead.ssh2.HTTPProxyData;
import com.trilead.ssh2.LocalPortForwarder;
import com.trilead.ssh2.ServerHostKeyVerifier;
import com.trilead.ssh2.Session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;

import ir.smartdevelopers.smarttunnel.exceptions.RemoteConnectionException;
import ir.smartdevelopers.smarttunnel.ui.classes.AcceptAllHostRepo;
import ir.smartdevelopers.smarttunnel.ui.classes.JschSimpleUserInfo;
import ir.smartdevelopers.smarttunnel.ui.exceptions.AuthFailedException;
import ir.smartdevelopers.smarttunnel.ui.models.HttpProxy;
import ir.smartdevelopers.smarttunnel.ui.models.LogItem;
import ir.smartdevelopers.smarttunnel.ui.models.PrivateKey;
import ir.smartdevelopers.smarttunnel.ui.models.Proxy;
import ir.smartdevelopers.smarttunnel.ui.utils.DNSUtil;
import ir.smartdevelopers.smarttunnel.ui.utils.Util;
import ir.smartdevelopers.smarttunnel.utils.Logger;
import ir.smartdevelopers.tun2socks.DynamicForwarder;

public class Ssh2RemoteConnection extends RemoteConnection {
    private Connection mConnection;
    private String serverAddress;
    private int serverPort;
    private String username;
    private String password;
    private PrivateKey privateKey;
    private Proxy mProxy;
    private boolean isServerNameLocked;
    private boolean isServerPortLocked;
    private boolean isUsernameLocked;
    private boolean preferIpV6;
    private String mDNSServer;
    private boolean isConnected;
    private SparseArray<LocalPortForwarder> mLocalPortForwarderSparseArray = new SparseArray<>();
    private SparseArray<DynamicPortForwarder> mDynamicForwarderArray = new SparseArray<>();


    private OnDisconnectListener mOnDisconnectListener;

    public Ssh2RemoteConnection(String serverAddress, int serverPort,
                                String username, String password,
                                boolean isServerNameLocked, boolean isServerPortLocked,
                                boolean isUsernameLocked, String DNSServer, boolean preferIpV6) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.username = username;
        this.password = password;
        this.isServerNameLocked = isServerNameLocked;

        this.isServerPortLocked = isServerPortLocked;
        this.isUsernameLocked = isUsernameLocked;
        this.preferIpV6 = preferIpV6;
        this.mDNSServer = DNSServer;
    }

    @Override
    public int startLocalPortForwarding(String localAddress, int localPort, String remoteAddress, int remotePort) throws RemoteConnectionException {
        try {
            LocalPortForwarder portForwarder = mConnection.createLocalPortForwarder(new InetSocketAddress(localAddress,localPort),remoteAddress,remotePort);
            mLocalPortForwarderSparseArray.put(localPort,portForwarder);
            return localPort;
        }catch (IOException e) {
            throw new RemoteConnectionException(e);
        }
    }

    @Override
    public void stopLocalPortForwarding(String localAddress, int localPort) throws RemoteConnectionException {
        try {
            LocalPortForwarder forwarder = mLocalPortForwarderSparseArray.get(localPort);
            if (forwarder != null){
                forwarder.close();
            }
        } catch (IOException e) {
            throw new RemoteConnectionException(e);
        }
    }

    @Override
    public DirectTCPChannel startDirectTCPChannel( String remoteAddress, int remotePort) throws RemoteConnectionException {
        JschDirectTcpChannel channel = new JschDirectTcpChannel(remoteAddress, remotePort);
        channel.start();
        return channel;
    }

    public void startDynamicForwarder(int port) throws RemoteConnectionException {
        try {
            DynamicPortForwarder portForwarder = mConnection.createDynamicPortForwarder(new InetSocketAddress("127.0.0.1",port));
            mDynamicForwarderArray.put(port,portForwarder);
        } catch (IOException e) {
            throw new RemoteConnectionException(e);
        }

    }

    @Override
    public void stopDirectTCPChannel(DirectTCPChannel channel) throws RemoteConnectionException {
        channel.stop();
    }

    @Override
    public void setProxy(Proxy proxy) {
        mProxy = proxy;
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void connect() throws RemoteConnectionException {


        if (privateKey != null) {

        }
        try {

            String proxyMessage = "";
            if (mProxy instanceof HttpProxy) {
                proxyMessage = String.format(" using proxy %s:%d", mProxy.getAddress(), mProxy.getPort());
            }
            if (isServerNameLocked) {
                Logger.logMessage(new LogItem(String.format("Connecting to SSH server %s", proxyMessage)));
            } else {
                Logger.logMessage(new LogItem(String.format("Connecting to SSH server %s:%d %s", serverAddress, serverPort, proxyMessage)));
            }
            if (!isUsernameLocked) {
                Logger.logMessage(new LogItem(String.format("Username : %s", username)));

            }
            Logger.logMessage(new LogItem(String.format("getting host ip via %s DNS server", mDNSServer)));
            String ip = DNSUtil.getIp(serverAddress, mDNSServer, preferIpV6);
            String serverAddr = ip == null ? serverAddress : ip;
            Logger.logDebug("Server address is =" + serverAddr);
            mConnection= new Connection(serverAddr,serverPort);
            mConnection.setTCPNoDelay(true);
//            mConnection.setCompression(true);


            if (mProxy instanceof HttpProxy) {
                mConnection.setProxyData(new HTTPProxyData(mProxy.getAddress(), mProxy.getPort()));
            }
            if (privateKey == null) {
                Logger.logMessage(new LogItem("Auth = password"));
            } else {
                Logger.logMessage(new LogItem("Auth = private key"));
            }

            mConnection.addConnectionMonitor(new ConnectionMonitor() {
                @Override
                public void connectionLost(Throwable reason) {
                    isConnected = false;
                    if (mOnDisconnectListener != null) {
                        mOnDisconnectListener.onDisconnected();
                    }
                }
            });
//            mConnection.setClient2ServerCiphers(new String[]{"aes128-cbc","aes128-ctr"});
//            mConnection.setServer2ClientCiphers(new String[]{"aes128-cbc","aes128-ctr"});
            ConnectionInfo connectionInfo = mConnection.connect();
            boolean success = false;
            try {
                success = mConnection.authenticateWithPassword(username,password);
            }catch (Exception e){
                mConnection.close();
                throw new AuthFailedException();
            }
            if (!success){
                mConnection.close();
                throw new AuthFailedException();
            }
            isConnected = true;
//            mSession.setPassword(password);
//            mSession.setUserInfo(new JschSimpleUserInfo());
//            mSession.setOnDisconnectListener(new Session.OnDisconnectListener() {
//                @Override
//                public void onDisconnected() {
//                    if (mOnDisconnectListener != null) {
//                        mOnDisconnectListener.onDisconnected();
//                    }
//                }
//            });
//            mSession.connect(15000);


            Logger.logStyledMessage("Connected to SSH server", "#4AD8E2", true);
        } catch (Exception e) {
            Logger.logStyledMessage("connection to SSH server failed", "#FFBA44", true);
            if (e instanceof AuthFailedException) {
                    Logger.logStyledMessage("Authentication failed", "#FFBA44", true);
                    throw new RemoteConnectionException(e);
            }
            Logger.logMessage(new LogItem("Error while connecting to SSH server : " + e.getMessage()));
            throw new RemoteConnectionException(e);
        }
    }



    @Override
    public void disconnect() {
        for (int i =0 ; i<mLocalPortForwarderSparseArray.size(); i++){
            try {
                mLocalPortForwarderSparseArray.valueAt(i).close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        for (int i =0 ; i<mDynamicForwarderArray.size(); i++){
            try {
                mDynamicForwarderArray.valueAt(i).close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (mConnection == null) {
            return;
        }
        mConnection.close();
        Logger.logMessage(new LogItem("SSH server disconnected"));
    }

    @Override
    public boolean isConnected() {
        if (mConnection == null) {
            return false;
        }
        return isConnected;
    }

    @Override
    public int getMainSocketDescriptor() {
        int fdVal = -1;
        if (mConnection != null && isConnected) {
            fdVal = Util.getSocketDescriptor(mConnection.getMainSocket());
        }

        return fdVal;
    }

    @Override
    public boolean isPortInUse(int port) {
        if (mDynamicForwarderArray.get(port) != null){
            return true;
        }
        return mLocalPortForwarderSparseArray.get(port) != null;
    }

    public void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public Ssh2RemoteConnection setOnDisconnectListener(OnDisconnectListener onDisconnectListener) {
        mOnDisconnectListener = onDisconnectListener;
        return this;
    }

    public class JschDirectTcpChannel extends DirectTCPChannel {
        Socket mSocket;
        String remoteAddress;
        int remotePort;
        int localPort;


        public JschDirectTcpChannel(String remoteAddress, int remotePort) {
            this.remoteAddress = remoteAddress;
            this.remotePort = remotePort;

        }

        @Override
        public void start() throws RemoteConnectionException {

            int localPort = getFreePort();
            if (localPort == 0){
                throw new RemoteConnectionException("can not finde free port");
            }
            localPort = startLocalPortForwarding("127.0.0.1", localPort, remoteAddress, remotePort);
            try {

                mSocket = new Socket(remoteAddress,remotePort);
                mSocket.setTcpNoDelay(true);
                setRemoteIn(mSocket.getInputStream());
                setRemoteOut(mSocket.getOutputStream());


//                mSocket = new Socket();
//                mSocket.setTcpNoDelay(true);
////                mSocket.setReceiveBufferSize(Packet.MAX_SIZE);
//                mSocket.connect(new InetSocketAddress(localAddress, localPort));

            } catch (Exception e) {
                stop();
                throw new RemoteConnectionException(e);
            }
        }
        private int getFreePort(){
            try (ServerSocket s = new ServerSocket(0)){
                return s.getLocalPort();
            } catch (IOException e) {
                // ignore
            }
            return 0;
        }

        @Override
        public void stop() throws RemoteConnectionException {
            try {
                if (getRemoteIn() != null) {
                    getRemoteIn().close();
                }
                if (getRemoteOut() != null) {
                    getRemoteOut().close();
                }
                if (mSocket != null) {
                    if (!mSocket.isClosed()) {
                        mSocket.close();
                    }
                }
                stopLocalPortForwarding("127.0.0.1",localPort);
            } catch (IOException e) {
                throw new RemoteConnectionException(e);
            }
        }

        @Override
        public boolean isConnected() {
            if (mSocket == null){
                return false;
            }
            return mSocket.isConnected();

        }

        @Override
        public void setTimeOut(int timeOut) {
            try {
                mSocket.setSoTimeout(timeOut);
            } catch (SocketException e) {
                //ignore
            }
        }
    }


}
