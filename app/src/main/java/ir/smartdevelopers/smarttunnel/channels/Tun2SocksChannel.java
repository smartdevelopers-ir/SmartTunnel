package ir.smartdevelopers.smarttunnel.channels;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

import engine.Key;
import ir.smartdevelopers.smarttunnel.exceptions.RemoteConnectionException;
import ir.smartdevelopers.smarttunnel.managers.ChannelManager;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.packet.PacketV4;
import ir.smartdevelopers.smarttunnel.ui.models.HttpProxy;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;
import engine.Engine;

public class Tun2SocksChannel extends ChannelV4TCP{

    private HttpProxy mProxy;
    private int mLocalPort;
    private Socket mSocket;

    public Tun2SocksChannel(HttpProxy proxy, String id, PacketV4 packetV4, RemoteConnection remoteConnection, ChannelManager channelManager) {
        super(id, packetV4, remoteConnection, channelManager);
        mProxy = proxy;

    }

    @Override
    public void close() {
        if (mSocket!=null){
            try {
                mSocket.close();
            } catch (IOException ignore) {}
        }
        try {
            mRemoteConnection.stopLocalPortForwarding("127.0.0.1",mLocalPort);
        } catch (RemoteConnectionException ignore) {}
        super.close();
    }

    @Override
    public boolean isConnected() {
        return mSocket != null && mSocket.isConnected();
    }

    @Override
    public void connect() throws RemoteConnectionException {
        if (!mRemoteConnection.isPortInUse(1080)){
            mLocalPort = mRemoteConnection.startLocalPortForwarding("127.0.0.1",1080,
                    mProxy.getAddress(),mProxy.getPort()); // squid address in server
        }else {
            mLocalPort = 1080;
        }
        Proxy proxy = new Proxy(Proxy.Type.SOCKS,new InetSocketAddress("127.0.0.1",1080));
        mSocket = new Socket(proxy);
        try {
            mSocket.setTcpNoDelay(true);
            mSocket.setReceiveBufferSize(Packet.MAX_SIZE);
            mSocket.connect(new InetSocketAddress(InetAddress.getByAddress(getRemoteAddress()),
                    ByteUtil.getIntValue(getRemotePort())));
            mRemoteIn = mSocket.getInputStream();
            mRemoteOut = mSocket.getOutputStream();
        } catch (Exception e) {
            throw new RemoteConnectionException(e);
        }


    }

}
