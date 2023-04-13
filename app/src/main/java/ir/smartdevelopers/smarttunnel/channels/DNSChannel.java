package ir.smartdevelopers.smarttunnel.channels;

import com.jcraft.jsch.ChannelDirectTCPIP;
import com.jcraft.jsch.Session;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

import ir.smartdevelopers.smarttunnel.managers.ChannelManager;
import ir.smartdevelopers.smarttunnel.packet.IPV4Header;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.packet.PacketV4;
import ir.smartdevelopers.smarttunnel.packet.UDP;
import ir.smartdevelopers.smarttunnel.utils.ArrayUtil;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;
import ir.smartdevelopers.smarttunnel.utils.Logger;

public class DNSChannel extends Channel {

    private Session mSession;
    private InputStream mRemoteIn;
    private OutputStream mRemoteOut;
    private ChannelDirectTCPIP mChannel;
    private final ChannelManager mChannelManager;
    private PacketV4 mInitialPacket;
    /**
     * We must write to remote out first then reade from remote input
     * so before we reade we must wait ro writer to release this lock
     */
    private Semaphore readerLock;

    /**
     * This data is when we have new data to be written in remote
     * when this is nut null we must write this to emote out
     * when done, set this to null
     */
//    private PacketV4 mData;
    public DNSChannel(String id,PacketV4 packetV4, Session session, ChannelManager channelManager) {
        super(id, packetV4.getTransmissionProtocol().getSourcePort()
                , packetV4.getTransmissionProtocol().getDestPort()
                , packetV4.getIPHeader().getSourceAddress()
                , packetV4.getIPHeader().getDestAddress());

        mSession = session;
        mChannelManager = channelManager;
        mInitialPacket = packetV4;
        readerLock = new Semaphore(0);
    }

    @Override
    public  void onNewPacket(Packet packet) {

        if (packet instanceof PacketV4) {
            PacketV4 pk = (PacketV4) packet;
            mInitialPacket = pk;
        }

    }


    private  void sendDataToClient(byte[] data) {
        Packet packet = makeUDPPacket(data, getRemoteAddress(), getLocalAddress(),
                getRemotePort(), getLocalPort());

        mChannelManager.sendToLocal(packet);

    }

    private  Packet makeUDPPacket(byte[] data, byte[] sourceAddress, byte[] destAddress,
                                              byte[] sourcePort, byte[] destPort) {
        int ipDataLength = 8 + (data == null ? 0 : data.length); // 8 is UDP header length
        IPV4Header header = generateHeader(sourceAddress, destAddress, ipDataLength);
        UDP udp = new UDP(sourcePort, destPort);
        return new PacketV4(header, udp, data);

    }

    /**
     * @param ipDataLength if is TCP it is TCP header length + data length
     *                     if it is UPD it must be UPD header length + data length
     */
    private IPV4Header generateHeader(byte[] sourceAddress, byte[] destAddress, int ipDataLength) {
        IPV4Header header = new IPV4Header(sourceAddress, destAddress);
        header.setFlag(mInitialPacket.getIPHeader().getFlag());
        header.setIdentification(mInitialPacket.getIPHeader().getIdentification());
        header.setProtocol(mInitialPacket.getProtocolNumber());
        header.setTimeToLive((byte) 30);
        header.setFragmentOffset(mInitialPacket.getIPHeader().getFragmentOffset());
        header.setTypeOfService(mInitialPacket.getIPHeader().getTypeOfService());
        header.setTotalLength(ByteUtil.getByteFromInt(header.getHeaderLength() + ipDataLength, 2));
        return header;
    }

    @Override
    public void run() {
        if (mSession == null || !mSession.isConnected()){
            return;
        }
        try {
            mChannel = (ChannelDirectTCPIP) mSession.openChannel("direct-tcpip");
            mChannel.setHost(mInitialPacket.getIPHeader().getDestAddressName());
            mChannel.setPort(mInitialPacket.getTransmissionProtocol().getDestPortIntValue());
            mRemoteOut = mChannel.getOutputStream();
            mRemoteIn = mChannel.getInputStream();
            mChannel.connect(5000);
            if (mChannel.isConnected()) {
                if (mInitialPacket.getData() != null) {
                    try {
                        byte[] outData = null;
                        outData = new byte[mInitialPacket.getData().length + 2];
                        int dataLength = mInitialPacket.getData().length;
                        byte[] lengthBytes = ByteUtil.getByteFromInt(dataLength, 2);
                        ArrayUtil.replace(outData, 0, lengthBytes);
                        ArrayUtil.replace(outData, 2, mInitialPacket.getData());

                        mRemoteOut.write(outData);
                        mRemoteOut.flush();
                        byte[] response = new byte[Packet.MAX_SIZE];
                        int len = mRemoteIn.read(response);
                        if (len > 0) {
                            sendDataToClient(Arrays.copyOfRange(response, 2, len));
                        }else {
                            Logger.logError("DNS paket read failed : " + Arrays.toString(outData));
                        }
                    } catch (IOException e) {
                        if (!(e instanceof SocketTimeoutException)) {
                            throw e;
                        }
                    } finally {
                        if (mRemoteIn != null) {
                            mRemoteIn.close();
                        }
                        if (mRemoteOut != null) {
                            mRemoteOut.close();
                        }
                    }


                }

            }


        } catch (Exception e) {
            Logger.logDebug(e.getMessage());
        }


        this.close();
    }


    @Override
    public void close() {
        if (mChannel != null) {
            mChannel.disconnect();
        }
        mChannelManager.removeChannel(this);
    }



}
