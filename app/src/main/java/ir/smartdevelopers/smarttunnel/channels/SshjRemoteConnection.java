package ir.smartdevelopers.smarttunnel.channels;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;

import net.schmizz.sshj.AndroidConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.DirectConnection;
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder;
import net.schmizz.sshj.connection.channel.direct.Parameters;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyFormat;
import net.schmizz.sshj.userauth.keyprovider.KeyPairWrapper;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil;
import net.schmizz.sshj.userauth.keyprovider.PuTTYKeyFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;

import ir.smartdevelopers.smarttunnel.exceptions.RemoteConnectionException;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.ui.classes.AcceptAllHostRepo;
import ir.smartdevelopers.smarttunnel.ui.models.PrivateKey;
import ir.smartdevelopers.smarttunnel.ui.models.Proxy;

public class SshjRemoteConnection extends RemoteConnection{

    private SSHClient mSSHClient;
    private String serverAddress;
    private int serverPort;
    private String username;
    private String password;
    private PrivateKey privateKey;
    private Proxy mProxy;
    private final HashMap<String,LocalPortForwarder> mPortForwarderMap;

    public SshjRemoteConnection(String serverAddress, int serverPort,
                                String username, String password) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.username = username;
        this.password = password;
        mPortForwarderMap=new HashMap<>();

    }
    @Override
    public int startLocalPortForwarding(String localAddress, int localPort, String remoteAddress, int remotePort) throws RemoteConnectionException {
        Parameters parameters = new Parameters(localAddress,localPort,remoteAddress,remotePort);
        try {
            ServerSocket serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(parameters.getLocalHost(),parameters.getLocalPort()));
            LocalPortForwarder forwarder = mSSHClient.newLocalPortForwarder(parameters,serverSocket);
            forwarder.listen();
            mPortForwarderMap.put(localAddress +":"+ serverSocket.getLocalPort(),forwarder);
            return serverSocket.getLocalPort();

        } catch (IOException e) {
            throw new RemoteConnectionException(e);
        }

    }

    @Override
    public void stopLocalPortForwarding(String localAddress, int localPort) throws RemoteConnectionException {
        LocalPortForwarder forwarder = mPortForwarderMap.get(localAddress +":"+ localPort);
        if (forwarder != null){
            try {
                forwarder.close();
            } catch (IOException e) {
                throw new RemoteConnectionException(e);
            }
        }
    }

    @Override
    public DirectTCPChannel startDirectTCPChannel(String localAddress, int localPort, String remoteAddress, int remotePort) throws RemoteConnectionException {
        SshjDirectChannel channel = new SshjDirectChannel(remoteAddress,remotePort);
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
        mSSHClient = new SSHClient(new AndroidConfig());

        JSch jSch = new JSch();
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

            mSSHClient.addHostKeyVerifier(new HostKeyVerifier() {
                @Override
                public boolean verify(String hostname, int port, PublicKey key) {
                    return true;
                }

                @Override
                public List<String> findExistingAlgorithms(String hostname, int port) {
                    return null;
                }
            });

            mSSHClient.connect(serverAddress,serverPort);
            mSSHClient.getSocket().setReceiveBufferSize(Packet.MAX_SIZE);
            mSSHClient.authPassword(username,password);


        } catch (IOException e) {
            throw new RemoteConnectionException(e);
        }
    }

    @Override
    public void disconnect() {
        try {
            mSSHClient.disconnect();
        } catch (IOException ignore) {

        }
    }

    @Override
    public boolean isConnected() {
        return mSSHClient.isConnected();
    }

    @Override
    public Socket getMainSocket() {
        return mSSHClient.getSocket();
    }

    public void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public class SshjDirectChannel extends DirectTCPChannel{
        String remoteAddress;
        int remotePort;
        DirectConnection directConnection;

        public SshjDirectChannel(String remoteAddress, int remotePort) {
            this.remoteAddress = remoteAddress;
            this.remotePort = remotePort;

        }

        @Override
        protected void start() throws RemoteConnectionException {

            try {
                 directConnection = mSSHClient.newDirectConnection(remoteAddress,remotePort);

                setRemoteIn(directConnection.getInputStream());
                setRemoteOut(directConnection.getOutputStream());

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
                if (directConnection != null){
                    directConnection.close();
                }

            } catch (IOException e) {
                throw new RemoteConnectionException(e);
            }
        }

        @Override
        public boolean isConnected() {

            return directConnection.isOpen();
        }
    }

}
