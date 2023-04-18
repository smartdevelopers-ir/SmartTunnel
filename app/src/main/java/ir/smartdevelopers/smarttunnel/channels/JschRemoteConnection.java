package ir.smartdevelopers.smarttunnel.channels;

import com.jcraft.jsch.ChannelDirectTCPIP;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.ProxyHTTP;
import com.jcraft.jsch.Session;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import ir.smartdevelopers.smarttunnel.exceptions.RemoteConnectionException;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.ui.classes.AcceptAllHostRepo;
import ir.smartdevelopers.smarttunnel.ui.models.HttpProxy;
import ir.smartdevelopers.smarttunnel.ui.models.PrivateKey;
import ir.smartdevelopers.smarttunnel.ui.models.Proxy;

public class JschRemoteConnection extends RemoteConnection {
    private Session mSession;
    private String serverAddress;
    private int serverPort;
    private String username;
    private String password;
    private PrivateKey privateKey;
    private Proxy mProxy;

    public JschRemoteConnection(String serverAddress, int serverPort,
                                String username, String password) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.username = username;
        this.password = password;

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
    public DirectTCPChannel startDirectTCPChannel(String localAddress, int localPort, String remoteAddress, int remotePort) throws RemoteConnectionException {
        JschDirectTcpChannel channel = new JschDirectTcpChannel(remoteAddress, remotePort, localAddress, localPort);
        channel.start();
        return channel;
    }

    @Override
    public void stopDirectTCPChannel(DirectTCPChannel channel) throws RemoteConnectionException {
        channel.stop();
    }

    @Override
    public void setProxy(Proxy proxy) {
        mProxy = proxy;
    }

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
            mSession = jSch.getSession(username, serverAddress, serverPort);
            if (mProxy instanceof HttpProxy) {
                mSession.setProxy(new ProxyHTTP(mProxy.getAddress(),mProxy.getPort()));
            }
            mSession.setPassword(password);
            mSession.connect(15000);
        }catch (Exception e){
            throw new RemoteConnectionException(e);
        }
    }

    @Override
    public void disconnect() {
        if (mSession == null){
            return;
        }
        mSession.disconnect();
    }

    @Override
    public boolean isConnected() {
        if (mSession == null){
            return false;
        }
        return mSession.isConnected();
    }

    @Override
    public Socket getMainSocket() {
        if (mSession == null){
            return null;
        }
        if (!mSession.isConnected()){
            return null;
        }
        return mSession.getSocket();
    }

    public void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public class JschDirectTcpChannel extends DirectTCPChannel {
        Socket mSocket;
        String remoteAddress;
        int remotePort;
        String localAddress;
        int localPort;
        private ChannelDirectTCPIP mChannel;

        public JschDirectTcpChannel(String remoteAddress, int remotePort, String localAddress, int localPort) {
            this.remoteAddress = remoteAddress;
            this.remotePort = remotePort;
            this.localAddress = localAddress;
            this.localPort = localPort;
        }

        @Override
        public void start() throws RemoteConnectionException {
            if (localAddress == null){
                localAddress = "127.0.0.1";
            }
            localPort = startLocalPortForwarding(localAddress, localPort, remoteAddress, remotePort);
            try {
                mSocket = new Socket();
                mSocket.setTcpNoDelay(true);
                mSocket.setReceiveBufferSize(Packet.MAX_SIZE);
                mSocket.connect(new InetSocketAddress(localAddress, localPort));
                setRemoteIn(mSocket.getInputStream());
                setRemoteOut(mSocket.getOutputStream());
            } catch (Exception e) {
                stop();
                throw new RemoteConnectionException(e);
            }
        }
//        @Override
//        public void start() throws RemoteConnectionException {
//            if (localAddress == null){
//                localAddress = "127.0.0.1";
//            }
//
//            try {
//                mChannel = (ChannelDirectTCPIP) mSession.openChannel("direct-tcpip");
//                mChannel.setHost(remoteAddress);
//                mChannel.setPort(remotePort);
//                PipedOutputStream out = new PipedOutputStream();
//                PipedInputStream selfIn = new PipedInputStream(out,Packet.MAX_SIZE);
//                PipedOutputStream selfOut = new PipedOutputStream();
//                PipedInputStream in = new PipedInputStream(selfOut,Packet.MAX_SIZE);
//                mChannel.setOutputStream(out,true);
//                mChannel.setInputStream(in,true);
//                setRemoteIn(selfIn);
//                setRemoteOut(selfOut);
//                mChannel.connect(15000);
//            } catch (Exception e) {
//                stop();
//                throw new RemoteConnectionException(e);
//            }
//        }

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
                if (mChannel != null){
                    mChannel.disconnect();
                }
                stopLocalPortForwarding(localAddress, localPort);
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
    }
}
