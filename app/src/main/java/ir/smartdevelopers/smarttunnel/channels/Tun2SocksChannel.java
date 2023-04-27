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

    private Socket mSocket;

    public Tun2SocksChannel( String id, PacketV4 packetV4, ChannelManager channelManager) {
        super(id, packetV4, channelManager);

    }

    @Override
    public void close() {
        if (mSocket!=null){
            try {
                mSocket.close();
            } catch (IOException ignore) {}
        }

        super.close();
    }

    @Override
    public boolean isConnected() {
        return mSocket != null && mSocket.isConnected();
    }

    @Override
    public void connect() throws RemoteConnectionException {

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
