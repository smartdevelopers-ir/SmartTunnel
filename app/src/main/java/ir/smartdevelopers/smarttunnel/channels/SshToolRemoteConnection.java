package ir.smartdevelopers.smarttunnel.channels;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.sshtools.client.SshClient;
import com.sshtools.client.SshClientContext;
import com.sshtools.common.permissions.UnauthorizedException;
import com.sshtools.common.publickey.InvalidPassphraseException;
import com.sshtools.common.publickey.OpenSSHPrivateKeyFileParser;
import com.sshtools.common.publickey.SshKeyPairGenerator;
import com.sshtools.common.publickey.SshKeyUtils;
import com.sshtools.common.ssh.SshException;
import com.sshtools.common.ssh.components.SshKeyPair;
import com.sshtools.common.ssh.components.SshPrivateKey;
import com.sshtools.common.ssh.components.jce.Ssh2RsaPrivateKey;
import com.sshtools.common.util.ByteArrayReader;
import com.sshtools.common.util.ByteArrayWriter;
import com.sshtools.synergy.nio.DefaultSocketConnectionFactory;
import com.sshtools.synergy.nio.SocketConnection;
import com.sshtools.synergy.nio.SocketConnectionFactory;
import com.sshtools.synergy.nio.SshEngineContext;
import com.sshtools.synergy.ssh.ConnectionManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

import ir.smartdevelopers.smarttunnel.exceptions.RemoteConnectionException;
import ir.smartdevelopers.smarttunnel.ui.models.PrivateKey;
import ir.smartdevelopers.smarttunnel.ui.models.Proxy;

public class SshToolRemoteConnection extends RemoteConnection{
    private SshClient mSshClient;
    private String serverAddress;
    private int serverPort;
    private String username;
    private String password;
    private PrivateKey privateKey;
    private SocketConnection mSocketConnection;

    public SshToolRemoteConnection(String serverAddress, int serverPort, String username, String password) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.username = username;
        this.password = password;
    }

    @Override
    public int startLocalPortForwarding(String localAddress, int localPort, String remoteAddress, int remotePort) throws RemoteConnectionException {
        try {
            mSshClient.getContext().getForwardingPolicy().allowForwarding();
            return mSshClient.startLocalForwarding(localAddress,localPort,remoteAddress,remotePort);
        } catch (Exception e) {
            throw new RemoteConnectionException(e);
        }
    }

    @Override
    public void stopLocalPortForwarding(String localAddress, int localPort) throws RemoteConnectionException {
        mSshClient.stopLocalForwarding(localAddress,localPort);
    }

    @Override
    public DirectTCPChannel startDirectTCPChannel(String localAddress, int localPort, String remoteAddress, int remotePort) throws RemoteConnectionException {
        SshToolDirectChannel channel = new SshToolDirectChannel(remoteAddress,remotePort,localAddress,localPort);
        channel.start();
        return channel;
    }

    @Override
    public void stopDirectTCPChannel(DirectTCPChannel channel) throws RemoteConnectionException {
        channel.stop();
    }

    @Override
    public void setProxy(Proxy proxy) {

    }

    @Override
    public void connect() throws RemoteConnectionException {
        SshKeyPair keyPair = null;
        if (privateKey != null) {
            try {
                keyPair = SshKeyUtils.getPrivateKey(privateKey.key,"");
            }  catch (Exception e) {
                throw new RemoteConnectionException(e);
            }
        }
        try {
//            SshClientContext context = new SshClientContext();
//            mSocketConnection = new SocketConnectionImpl();
//
//            SocketConnectionFactory factory = new SocketConnectionFactory() {
//                @Override
//                public SocketConnection createSocketConnection(SshEngineContext context, SocketAddress localAddress, SocketAddress remoteAddress) throws IOException {
//                    return mSocketConnection;
//                }
//            };
//
//            context.setSocketConnectionFactory(factory);

            mSshClient = new SshClient(serverAddress, serverPort, username, password.toCharArray(),keyPair );

        } catch (Exception e) {
            throw new RemoteConnectionException(e);
        }
    }

    @Override
    public void disconnect() {
        if (mSshClient!=null){
            mSshClient.disconnect();
        }
    }

    @Override
    public boolean isConnected() {
        if (mSshClient == null){
            return  false;
        }
        return mSshClient.isConnected();
    }

    @Override
    public Socket getMainSocket() {
        if (mSocketConnection == null || mSocketConnection.getSocketChannel() == null){
            return null;
        }
        return null;
//        return mSocketConnection.getSocketChannel().socket();
    }

    @Override
    public boolean isPortInUse(int port) {
        return mSshClient.getContext().getForwardingManager().isListening(port);

    }

    public void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public class SshToolDirectChannel extends DirectTCPChannel{
        Socket mSocket;
        String remoteAddress;
        int remotePort;
        String localAddress;
        int localPort;

        public SshToolDirectChannel(String remoteAddress, int remotePort, String localAddress, int localPort) {
            this.remoteAddress = remoteAddress;
            this.remotePort = remotePort;
            this.localAddress = localAddress;
            this.localPort = localPort;
        }

        @Override
        protected void start() throws RemoteConnectionException {
            if (localAddress == null){
                localAddress = "127.0.0.1";
            }
            localPort = startLocalPortForwarding(localAddress, localPort, remoteAddress, remotePort);
            try {
                mSocket = new Socket();
                mSocket.setTcpNoDelay(true);
                mSocket.connect(new InetSocketAddress(localAddress, localPort));
                setRemoteIn(mSocket.getInputStream());
                setRemoteOut(mSocket.getOutputStream());
            } catch (Exception e) {
                stop();
                throw new RemoteConnectionException(e);
            }
        }

        @Override
        protected void stop() throws RemoteConnectionException {
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

    private class SocketConnectionImpl extends SocketConnection{
        public SocketConnectionImpl() throws IOException {
            socketChannel = SocketChannel.open();
        }
    }
}
