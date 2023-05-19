package ir.smartdevelopers.smarttunnel.channels;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Random;

import ir.smartdevelopers.smarttunnel.exceptions.RemoteConnectionException;
import ir.smartdevelopers.smarttunnel.managers.ChannelManager;
import ir.smartdevelopers.smarttunnel.packet.IPV4Header;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.packet.PacketV4;
import ir.smartdevelopers.smarttunnel.packet.TCP;
import ir.smartdevelopers.smarttunnel.packet.TCPPacketWrapper;
import ir.smartdevelopers.smarttunnel.packet.TransmissionProtocol;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;
import ir.smartdevelopers.smarttunnel.utils.Logger;

public class SshChannelV4TCP extends ChannelV4TCP implements TCPController.TcpListener {

    private RemoteConnection.DirectTCPChannel mChannel;
    private RemoteConnection mRemoteConnection;

    public SshChannelV4TCP(String id, PacketV4 packetV4, RemoteConnection remoteConnection, ChannelManager channelManager) {
        super(id, packetV4,  channelManager);
        mRemoteConnection = remoteConnection;
    }


    /**
     * close connections
     */
    @Override
    public void close() {
        if (mChannel != null) {
            try {
                mRemoteConnection.stopDirectTCPChannel(mChannel);
            } catch (RemoteConnectionException ignore) {

            }
        }
        super.close();

    }

    @Override
    public boolean isConnected() {
        return mChannel!=null && mChannel.isConnected();
    }

    @Override
    public void connect() throws RemoteConnectionException {

            mChannel = mRemoteConnection.startDirectTCPChannel(mInitialPacket.getIPHeader().getDestAddressName(),
                    mInitialPacket.getTransmissionProtocol().getDestPortIntValue());

            mRemoteOut = mChannel.getRemoteOut();
            mRemoteIn = mChannel.getRemoteIn();

    }


}
