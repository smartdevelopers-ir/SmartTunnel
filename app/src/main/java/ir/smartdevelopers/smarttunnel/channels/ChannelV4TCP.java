package ir.smartdevelopers.smarttunnel.channels;

import com.jcraft.jsch.ChannelDirectTCPIP;
import com.jcraft.jsch.Session;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.SocketTimeoutException;
import java.util.Arrays;

import ir.smartdevelopers.smarttunnel.managers.ChannelManager;
import ir.smartdevelopers.smarttunnel.packet.IPV4Header;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.packet.PacketV4;
import ir.smartdevelopers.smarttunnel.packet.TCP;
import ir.smartdevelopers.smarttunnel.packet.TCPPacketWrapper;
import ir.smartdevelopers.smarttunnel.packet.TransmissionProtocol;
import ir.smartdevelopers.smarttunnel.utils.Logger;

public class ChannelV4TCP extends Channel implements TCPController.TcpListener {

    private final Session mSession;
    /**
     * We read every thing remote sends to us from this inputStream. This is connectred to
     * {@link #remoteSelfOutStream}
     */
//    private PipedInputStream mRemoteIn;
    private InputStream mRemoteIn;
    /**
     * every thing remote sends to us is written to this output stream
     */
    private PipedOutputStream remoteSelfOutStream;
    private OutputStream mRemoteOut;
    private int mMaxSegmentSize;
    private Thread mRemoteReaderThread;
    private ChannelDirectTCPIP mChannel;
    private final ChannelManager mChannelManager;
    /**
     * This is first packet we received from sender or local client
     */
    private PacketV4 mInitialPacket;

    private TCPController mTCPController;


    public ChannelV4TCP(String id, PacketV4 packetV4, Session session, ChannelManager channelManager) {
        super(id, packetV4.getTransmissionProtocol().getSourcePort()
                , packetV4.getTransmissionProtocol().getDestPort()
                , packetV4.getIPHeader().getSourceAddress()
                , packetV4.getIPHeader().getDestAddress());
        mSession = session;
        mChannelManager = channelManager;
        mInitialPacket = packetV4;
        mTCPController = new TCPController(packetV4, new Packet.PacketCreator() {
            @Override
            public Packet create(TransmissionProtocol transmissionProtocol, byte[] data) {
                PacketV4 pk = new PacketV4(generateHeader(getRemoteAddress(),getLocalAddress()),
                        transmissionProtocol,data);
                return pk;
            }
        }, id, this);
        if (((TCP) packetV4.getTransmissionProtocol()).getTCPOption() != null) {
            if (((TCP) packetV4.getTransmissionProtocol()).getTCPOption().getMaximumSegmentSize() == 0) {
                mMaxSegmentSize = 2048;
            } else {
                mMaxSegmentSize = ((TCP) packetV4.getTransmissionProtocol()).getTCPOption().getMaximumSegmentSize();
            }
        }


    }

    @Override
    public void onNewPacket(Packet packet) {

        mTCPController.packetFromClient(packet);

    }


    public void onConnectionEstablished() {
        startReaderThread();
    }

    public void onPacketReadyForClient(Packet packet) {
        mChannelManager.sendToLocal(packet);
    }

    @Override
    public void onTermination() {
        this.close();
    }

    private void startReaderThread() {
        mRemoteReaderThread = new Thread(new ChannelReader(mChannel, this));
        mRemoteReaderThread.setName(getId() + "_reader");
        mRemoteReaderThread.start();

    }


    private IPV4Header generateHeader(byte[] sourceAddress, byte[] destAddress) {
        IPV4Header header = new IPV4Header(sourceAddress, destAddress);
        header.setFlag(mInitialPacket.getIPHeader().getFlag());
        header.setIdentification(mInitialPacket.getIPHeader().getIdentification());
        header.setProtocol(TCP.PROTOCOL_NUMBER);
        header.setTimeToLive((byte) 30);
        header.setFragmentOffset(mInitialPacket.getIPHeader().getFragmentOffset());
        header.setTypeOfService(mInitialPacket.getIPHeader().getTypeOfService());
        return header;
    }

    @Override
    public void run() {
        if (mSession == null || !mSession.isConnected()) {
            return;
        }
        try {
            mChannel = (ChannelDirectTCPIP) mSession.openChannel("direct-tcpip");
            mChannel.setHost(mInitialPacket.getIPHeader().getDestAddressName());
            mChannel.setPort(mInitialPacket.getTransmissionProtocol().getDestPortIntValue());
            remoteSelfOutStream = new PipedOutputStream();
//            mRemoteIn = new PipedInputStream(remoteSelfOutStream, Packet.MAX_SIZE);
//            mChannel.setOutputStream(remoteSelfOutStream, false);
            mRemoteOut = mChannel.getOutputStream();
            mRemoteIn = mChannel.getInputStream();
            mChannel.connect(5000);

            mTCPController.onChannelConnected();

            while (true) {
                // wait for handshake paket first then
                // for other packet

                TCPPacketWrapper pkw = mTCPController.getRemotePacketQueue().poll();

                if (mChannel.isConnected()) {

                    if (pkw.getPacket().getData() != null && pkw.getPacket().getData().length > 0) {
                        try {

                            mRemoteOut.write(pkw.getPacket().getData());
                            if (pkw.isPush()) {
                                mRemoteOut.flush();
                            }
                        } catch (IOException e) {
                            if (!(e instanceof SocketTimeoutException)) {
                                throw e;
                            }
                        }

                    }


                } else {
                    break;
                }
            }

        } catch (Exception e) {
            Logger.logDebug(e.getMessage());
        }


        this.close();
    }

    /**
     * close connections
     */
    @Override
    public void close() {
        if (mChannel != null) {
            mChannel.disconnect();
        }
        mChannelManager.removeChannel(this);
        if (mRemoteReaderThread != null) {
            mRemoteReaderThread.interrupt();
        }
        mTCPController.terminate();

    }




    public static class ChannelReader implements Runnable {

        private final ChannelDirectTCPIP mChannel;
        private final ChannelV4TCP mChannelV4TCP;

        public ChannelReader(ChannelDirectTCPIP channel, ChannelV4TCP channelV4TCP) {
            mChannel = channel;
            mChannelV4TCP = channelV4TCP;

        }

        @Override
        public void run() {

            if (mChannel != null && mChannel.isConnected()) {
                try {
//                    byte[] buffer = new byte[mChannelV4TCP.mTCPController.getMaxSegmentSize()];
                    byte[] buffer = new byte[1024*4];
                    int len;
                    boolean psh = false;
                    while (true) {
                        if (mChannel.isConnected()) {
//                            mChannelV4TCP.mTCPController.waitIfWindowIsFull();
                            len = mChannelV4TCP.mRemoteIn.read(buffer);
                            if (len > 0) {
                                if (len < buffer.length || (len == buffer.length && mChannelV4TCP.mRemoteIn.available() == 0)) {
                                    psh = true;
                                }else {
                                    psh = false;
                                }
                                byte[] data = Arrays.copyOfRange(buffer, 0, len);
                                mChannelV4TCP.mTCPController.packetFromRemote(data, psh);
                                if (mChannelV4TCP.mRemoteIn.available() <= 0 && mChannel.isEOF()) {
                                    mChannelV4TCP.mTCPController.close();
                                    // wait to close from tcp controller
                                }
                            } else {
                                break;
                            }

                        } else {
                            break;
                        }
                    }
                } catch (IOException e) {
                    Logger.logDebug(e.getMessage());
                }
            }

            mChannelV4TCP.close();

        }
    }


}
