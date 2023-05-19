package ir.smartdevelopers.smarttunnel.channels;

import android.annotation.SuppressLint;

import com.jcraft.jsch.ChannelDirectTCPIP;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.ProxyHTTP;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;

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

public class JschRemoteConnection extends RemoteConnection {
    private Session mSession;
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
    private DynamicForwarder mDynamicForwarder;
    private OnDisconnectListener mOnDisconnectListener;

    public JschRemoteConnection(String serverAddress, int serverPort,
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
            return mSession.setPortForwardingL(localAddress, localPort, remoteAddress, remotePort);
        } catch (JSchException e) {
            throw new RemoteConnectionException(e);
        }
    }

    @Override
    public void stopLocalPortForwarding(String localAddress, int localPort) throws RemoteConnectionException {
        try {
            mSession.delPortForwardingL(localAddress, localPort);
        } catch (JSchException e) {
            throw new RemoteConnectionException(e);
        }
    }

    @Override
    public DirectTCPChannel startDirectTCPChannel( String remoteAddress, int remotePort) throws RemoteConnectionException {
        JschDirectTcpChannel channel = new JschDirectTcpChannel(remoteAddress, remotePort);
        channel.start();
        return channel;
    }

    public void startDynamicForwarder(int port) {
        mDynamicForwarder = new DynamicForwarder(port, mSession);

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
        JSch jSch = new JSch();
        jSch.setHostKeyRepository(new AcceptAllHostRepo());


        if (privateKey != null) {
            try {
                KeyPair keyPair = KeyPair.load(jSch, privateKey.key.getBytes(), null);
                ByteArrayOutputStream prKeyStream = new ByteArrayOutputStream();
                keyPair.writePrivateKey(prKeyStream);
                byte[] prKey = prKeyStream.toByteArray();
                jSch.addIdentity(keyPair.getPublicKeyComment(), prKey, keyPair.getPublicKeyBlob(), null);
            } catch (JSchException e) {
                throw new RemoteConnectionException(e);
            }
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
            mSession = jSch.getSession(username, serverAddr, serverPort);
//            mSession.setConfig("cipher.s2c", "aes256-cbc,3des-cbc,blowfish-cbc");
//            mSession.setConfig("cipher.c2s", "aes256-cbc,3des-cbc,blowfish-cbc");
//            mSession.setConfig("CheckCiphers", "aes256-cbc");

            if (mProxy instanceof HttpProxy) {
                mSession.setProxy(new ProxyHTTP(mProxy.getAddress(), mProxy.getPort()));
            }
            if (privateKey == null) {
                Logger.logMessage(new LogItem("Auth = password"));
            } else {
                Logger.logMessage(new LogItem("Auth = private key"));
            }
            mSession.setPassword(password);
            mSession.setUserInfo(new JschSimpleUserInfo());
            mSession.setOnDisconnectListener(new Session.OnDisconnectListener() {
                @Override
                public void onDisconnected() {
                    if (mOnDisconnectListener != null) {
                        mOnDisconnectListener.onDisconnected();
                    }
                }
            });
            mSession.connect(15000);


            Logger.logStyledMessage("Connected to SSH server", "#4AD8E2", true);
        } catch (Exception e) {
            Logger.logStyledMessage("connection to SSH server failed", "#FFBA44", true);
            if (e instanceof JSchException) {
                if (Util.contains(e.getMessage(), "Auth fail") || Util.contains(e.getMessage(), "Auth cancel")) {
                    Logger.logStyledMessage("Authentication failed", "#FFBA44", true);
                    throw new RemoteConnectionException(new AuthFailedException());
                }

            }
            Logger.logMessage(new LogItem("Error while connecting to SSH server : " + e.getMessage()));
            throw new RemoteConnectionException(e);
        }
    }



    @Override
    public void disconnect() {
        if (mDynamicForwarder != null) {
            mDynamicForwarder.stop();
        }
        if (mSession == null) {
            return;
        }
        mSession.disconnect();
        Logger.logMessage(new LogItem("SSH server disconnected"));
    }

    @Override
    public boolean isConnected() {
        if (mSession == null) {
            return false;
        }
        return mSession.isConnected();
    }

    @Override
    public int getMainSocketDescriptor() {
        int fdVal = -1;
        if (mSession != null && mSession.isConnected()) {
            fdVal = Util.getSocketDescriptor(mSession.getSocket());
        }

        return fdVal;
    }

    @Override
    public boolean isPortInUse(int port) {
        try {
            String[] forwarding = mSession.getPortForwardingL();
            for (String s : forwarding) {
                String[] splits = s.split(":");
                int lPort = Integer.parseInt(splits[0]);
                if (lPort == port) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public JschRemoteConnection setOnDisconnectListener(OnDisconnectListener onDisconnectListener) {
        mOnDisconnectListener = onDisconnectListener;
        return this;
    }

    public class JschDirectTcpChannel extends DirectTCPChannel {
        Socket mSocket;
        String remoteAddress;
        int remotePort;
        private ChannelDirectTCPIP mChannel;

        public JschDirectTcpChannel(String remoteAddress, int remotePort) {
            this.remoteAddress = remoteAddress;
            this.remotePort = remotePort;

        }

        @Override
        public void start() throws RemoteConnectionException {


//            if (localAddress == null){
//                localAddress = "127.0.0.1";
//            }
//            localPort = startLocalPortForwarding(localAddress, localPort, remoteAddress, remotePort);
            try {
                mChannel = (ChannelDirectTCPIP) mSession.openChannel("direct-tcpip");
                setRemoteIn(mChannel.getInputStream());
                setRemoteOut(mChannel.getOutputStream());
                mChannel.setHost(remoteAddress);
                mChannel.setPort(remotePort);
                mChannel.connect(10000);


//                mSocket = new Socket();
//                mSocket.setTcpNoDelay(true);
////                mSocket.setReceiveBufferSize(Packet.MAX_SIZE);
//                mSocket.connect(new InetSocketAddress(localAddress, localPort));

            } catch (Exception e) {
                stop();
                throw new RemoteConnectionException(e);
            }
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
                if (mChannel != null) {
                    mChannel.disconnect();
                }
//                stopLocalPortForwarding(localAddress, localPort);
            } catch (IOException e) {
                throw new RemoteConnectionException(e);
            }
        }

        @Override
        public boolean isConnected() {
//            if (mSocket == null){
//                return false;
//            }
//            return mSocket.isConnected();
            if (mChannel != null) {
                return mChannel.isConnected();
            }
            return false;
        }

        @Override
        public void setTimeOut(int timeOut) {

        }
    }


}
