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

public abstract class ChannelV4TCP extends Channel implements TCPController.TcpListener {

    protected RemoteConnection mRemoteConnection;
    /**
     * We read every thing remote sends to us from this inputStream.
     */

    protected InputStream mRemoteIn;
    /**
     * every thing remote sends to us is written to this output stream
     */
    protected OutputStream mRemoteOut;

    protected Thread mRemoteReaderThread;
    protected final ChannelManager mChannelManager;
    /**
     * This is first packet we received from sender or local client
     */
    protected PacketV4 mInitialPacket;

    protected TCPController mTCPController;
    /** This is IP header identification, we must increase by 1 for every packet sent to client */
    private short mIpIdentification;

    public ChannelV4TCP(String id, PacketV4 packetV4, RemoteConnection remoteConnection, ChannelManager channelManager) {
        super(id, packetV4.getTransmissionProtocol().getSourcePort()
                , packetV4.getTransmissionProtocol().getDestPort()
                , packetV4.getIPHeader().getSourceAddress()
                , packetV4.getIPHeader().getDestAddress());
        mRemoteConnection = remoteConnection;
        mChannelManager = channelManager;
        mInitialPacket = packetV4;
        mTCPController = new TCPController(packetV4, new TcpPacketCreator(), id, this);
        mIpIdentification = (short) new Random().nextInt(Short.MAX_VALUE);


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
        mRemoteReaderThread = new Thread(new ChannelReader());
        mRemoteReaderThread.setName(getId() + "_reader");
        mRemoteReaderThread.start();

    }


    private IPV4Header generateHeader(byte[] sourceAddress, byte[] destAddress) {
        IPV4Header header = new IPV4Header(sourceAddress, destAddress);
        header.setFlag(mInitialPacket.getIPHeader().getFlag());
        header.setIdentification(ByteUtil.getByteFromInt(mIpIdentification++,2));
        header.setProtocol(TCP.PROTOCOL_NUMBER);
        header.setTimeToLive((byte) 60);
        header.setFragmentOffset(mInitialPacket.getIPHeader().getFragmentOffset());
        header.setTypeOfService(mInitialPacket.getIPHeader().getTypeOfService());
        return header;
    }

    @Override
    public void run() {
        try {
            connect();

            mTCPController.onChannelConnected();

            while (true) {
                // wait for handshake paket first then
                // for other packet

                TCPPacketWrapper pkw = mTCPController.getRemotePacketQueue().poll();

                if (isConnected()) {

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
        mChannelManager.removeChannel(this);
        if (mRemoteReaderThread != null) {
            mRemoteReaderThread.interrupt();
        }
        mTCPController.terminate();

    }

    public abstract boolean isConnected();
    public abstract void connect() throws RemoteConnectionException;



    public  class ChannelReader implements Runnable {

//        private final ChannelDirectTCPIP mChannel;

        public ChannelReader( ) {

        }

        @Override
        public void run() {

            if (isConnected()) {
                try {
                    byte[] buffer = new byte[mTCPController.getMaxSegmentSize()];
//                    byte[] buffer = new byte[1024*4];
                    int len;
                    boolean psh = false;
                    while (true) {
                        if (isConnected()) {
                            mTCPController.waitIfWindowIsFull();
                            len = mRemoteIn.read(buffer);
                            if (len > 0) {
                                if (len < buffer.length || (len == buffer.length && mRemoteIn.available() == 0)) {
                                    psh = true;
                                }else {
                                    psh = false;
                                }
                                byte[] data = Arrays.copyOfRange(buffer, 0, len);
                                mTCPController.packetFromRemote(data, psh);
                                if (mRemoteIn.available() <= 0 && !isConnected()) {
                                    mTCPController.close();
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

            close();

        }
    }

    private class TcpPacketCreator implements Packet.PacketCreator{

        @Override
        public Packet create(TransmissionProtocol transmissionProtocol, byte[] data) {
            PacketV4 pk = new PacketV4(generateHeader(getRemoteAddress(),getLocalAddress()),
                    transmissionProtocol,data);
            return pk;
        }
    }

}
