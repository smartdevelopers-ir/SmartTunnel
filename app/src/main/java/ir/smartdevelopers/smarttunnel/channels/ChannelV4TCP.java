package ir.smartdevelopers.smarttunnel.channels;

import com.jcraft.jsch.ChannelDirectTCPIP;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.sshtools.client.SshClient;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Random;

import ir.smartdevelopers.smarttunnel.managers.ChannelManager;
import ir.smartdevelopers.smarttunnel.packet.IPV4Header;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.packet.PacketV4;
import ir.smartdevelopers.smarttunnel.packet.TCP;
import ir.smartdevelopers.smarttunnel.packet.TCPPacketWrapper;
import ir.smartdevelopers.smarttunnel.packet.TransmissionProtocol;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;
import ir.smartdevelopers.smarttunnel.utils.Logger;

public class ChannelV4TCP extends Channel implements TCPController.TcpListener {

    private final Session mSession;
    /**
     * We read every thing remote sends to us from this inputStream.
     */

    private InputStream mRemoteIn;
    /**
     * every thing remote sends to us is written to this output stream
     */
//    private PipedOutputStream remoteSelfOutStream;
    private OutputStream mRemoteOut;
    private int mMaxSegmentSize;
    private Thread mRemoteReaderThread;
    private final ChannelManager mChannelManager;
    /**
     * This is first packet we received from sender or local client
     */
    private PacketV4 mInitialPacket;

    private TCPController mTCPController;
    private Socket socket;
    private int mLocalPort;
    /** This is IP header identification, we must increase by 1 for every packet sent to client */
    private short mIpIdentification;

    public ChannelV4TCP(String id, PacketV4 packetV4, Session session, ChannelManager channelManager) {
        super(id, packetV4.getTransmissionProtocol().getSourcePort()
                , packetV4.getTransmissionProtocol().getDestPort()
                , packetV4.getIPHeader().getSourceAddress()
                , packetV4.getIPHeader().getDestAddress());
        mSession = session;
        mChannelManager = channelManager;
        mInitialPacket = packetV4;
        mTCPController = new TCPController(packetV4, new TcpPacketCreator(), id, this);
        if (((TCP) packetV4.getTransmissionProtocol()).getTCPOption() != null) {
            if (((TCP) packetV4.getTransmissionProtocol()).getTCPOption().getMaximumSegmentSize() == 0) {
                mMaxSegmentSize = 2048;
            } else {
                mMaxSegmentSize = ((TCP) packetV4.getTransmissionProtocol()).getTCPOption().getMaximumSegmentSize();
            }
        }
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
//        if (mSession == null || !mSession.isConnected()) {
//            return;
//        }
        try {
//            sshClient = new SshClient("s3.goolha.tk",442,"mostafa","mosi.1371".toCharArray());
//            sshClient.getContext().getForwardingPolicy().allowForwarding();
//            int port = sshClient.startLocalForwarding("127.0.0.1",0,
//                    mInitialPacket.getIPHeader().getDestAddressName(),
//                    mInitialPacket.getTransmissionProtocol().getDestPortIntValue());
//            socket = new Socket();
//            socket.setTcpNoDelay(true);
//            socket.setReceiveBufferSize(1024*4);
//            socket.connect(new InetSocketAddress("127.0.0.1",port));

            mLocalPort = mSession.setPortForwardingL("127.0.0.1",0,
                    mInitialPacket.getIPHeader().getDestAddressName(),
                    mInitialPacket.getTransmissionProtocol().getDestPortIntValue());
//            mChannel = (ChannelDirectTCPIP) mSession.openChannel("direct-tcpip");
//            mChannel.setHost(mInitialPacket.getIPHeader().getDestAddressName());
//            mChannel.setPort(mInitialPacket.getTransmissionProtocol().getDestPortIntValue());
//            remoteSelfOutStream = new PipedOutputStream();
//            mRemoteIn = new PipedInputStream(remoteSelfOutStream, Packet.MAX_SIZE);
//            mChannel.setOutputStream(remoteSelfOutStream, false);
            socket = new Socket();
            socket.setTcpNoDelay(true);
//            socket.setReceiveBufferSize(Packet.MAX_SIZE);
            socket.connect(new InetSocketAddress("127.0.0.1",mLocalPort));
            mRemoteOut = socket.getOutputStream();
            mRemoteIn = new BufferedInputStream(socket.getInputStream(),Packet.MAX_SIZE);
//            mChannel.connect(5000);

            mTCPController.onChannelConnected();

            while (true) {
                // wait for handshake paket first then
                // for other packet

                TCPPacketWrapper pkw = mTCPController.getRemotePacketQueue().poll();

                if (socket.isConnected()) {

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
//        if (sshClient != null) {
//            sshClient.disconnect();
//        }
        try {
            mSession.delPortForwardingL(mLocalPort);
        } catch (JSchException ignore) {

        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignore) {

            }

        }
        mChannelManager.removeChannel(this);
        if (mRemoteReaderThread != null) {
            mRemoteReaderThread.interrupt();
        }
        mTCPController.terminate();

    }




    public  class ChannelReader implements Runnable {

//        private final ChannelDirectTCPIP mChannel;

        public ChannelReader( ) {

        }

        @Override
        public void run() {

            if (socket != null && socket.isConnected()) {
                try {
                    byte[] buffer = new byte[mTCPController.getMaxSegmentSize()];
//                    byte[] buffer = new byte[1024*4];
                    int len;
                    boolean psh = false;
                    while (true) {
                        if (socket.isConnected()) {
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
                                if (mRemoteIn.available() <= 0 && socket.isClosed()) {
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
