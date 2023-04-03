package ir.smartdevelopers.smarttunnel.channels;

import android.util.Log;

import com.jcraft.jsch.ChannelDirectTCPIP;
import com.jcraft.jsch.Session;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import ir.smartdevelopers.smarttunnel.managers.ChannelManager;
import ir.smartdevelopers.smarttunnel.packet.IPV4Header;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.packet.PacketV4;
import ir.smartdevelopers.smarttunnel.packet.TCP;
import ir.smartdevelopers.smarttunnel.packet.TCPFlag;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;
import ir.smartdevelopers.smarttunnel.utils.Logger;

public class ChannelV4TCP extends Channel {

    private final Session mSession;
    /**
     * We read every thing remote sends to us from this inputStream. This is connectred to
     * {@link #remoteSelfOutStream}
     */
    private PipedInputStream mRemoteIn;
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
    /**
     * We add all packets that must write to remote in this queue
     * then release writer lock so writer thread read from this queue and writes to remote out
     **/
    private final ConcurrentLinkedQueue<PacketV4> mPacketQueue;
    private final Semaphore writerLock;
    /**
     * We must write to remote out first then reade from remote input
     * so before we reade we must wait ro writer to release this lock
     */
    private final Semaphore readerLock;


    /**
     * Server must send this seq number to client
     */
    private int mServerSequenceNumber;
    /**
     * Server must send this seq number to client
     */
    private int mServerAcknowledgeNumber;
    /**
     * We expect client send this ack number to us and when we creating
     * new packet we must use this as packet sequence number
     */
    private int mNextAcknowledgeNumber;
    /** We expect client sends this sequenceNumber to us
     * when client sends data to us we calculate this number
     * or when we sending data or ack to client */
    private int mNextSequenceNumber;
    /**
     * This processor process new packet in another thread
     */
    private final PacketProcessor mPacketProcessor;
    private State mState;
    private Thread mTimerThread;
    private KeepAliveThread mKeepAliveThread;

    private enum State {
        /**
         * Waiting for a connection request from any remote TCP end-point.
         */
        LISTEN,
        /**
         * Waiting for a confirming connection request acknowledgment after having
         * both received and sent a connection request.
         */
        SYN_RECEIVED,
        /**
         * An open connection, data received can be delivered to the user.
         * The normal state for the data transfer phase of the connection.
         */
        ESTABLISHED,
        /**
         * Waiting for a connection termination request from the remote TCP,
         * or an acknowledgment of the connection termination request previously sent.
         */
        FIN_WAIT_1,
        /**
         * Waiting for a connection termination request from the remote TCP.
         */
        FIN_WAIT_2,
        /**
         * Waiting for a connection termination request from the local user.
         */
        CLOSE_WAIT,
        /**
         * Waiting for a connection termination request acknowledgment from the remote TCP.
         */
        CLOSING,
        /**
         * Waiting for an acknowledgment of the connection termination request previously s
         * ent to the remote TCP (which includes an acknowledgment of
         * its connection termination request).
         */
        LAST_ACK,
        /**
         * Waiting for enough time to pass to be sure that all remaining packets on
         * the connection have expired.
         */
        TIME_WAIT,
        /**
         * No connection state at all.
         */
        CLOSED
    }


    public ChannelV4TCP(String id, PacketV4 packetV4, Session session, ChannelManager channelManager) {
        super(id, packetV4.getTransmissionProtocol().getSourcePort()
                , packetV4.getTransmissionProtocol().getDestPort()
                , packetV4.getIPHeader().getSourceAddress()
                , packetV4.getIPHeader().getDestAddress());
        mSession = session;
        mChannelManager = channelManager;
        mInitialPacket = packetV4;
        mState = State.LISTEN;
        if (((TCP) packetV4.getTransmissionProtocol()).getTCPOption() != null) {
            if (((TCP) packetV4.getTransmissionProtocol()).getTCPOption().getMaximumSegmentSize() == 0) {
                mMaxSegmentSize = 2048;
            } else {
                mMaxSegmentSize = ((TCP) packetV4.getTransmissionProtocol()).getTCPOption().getMaximumSegmentSize();
            }
        }

        mPacketQueue = new ConcurrentLinkedQueue<>();
        writerLock = new Semaphore(0);
        readerLock = new Semaphore(0);
        mPacketProcessor = new PacketProcessor(this);
        mPacketProcessor.start();
        mKeepAliveThread = new KeepAliveThread(this,60);
    }

    @Override
    public void onNewPacket(Packet packet) {

        if (packet instanceof PacketV4) {
            PacketV4 pk = (PacketV4) packet;
            if (pk.getIPHeader().getDestAddressName().equals("88.99.231.246")){
                TCP tcp = (TCP) pk.getTransmissionProtocol();
                Log.v("TTT",String.format("onNewPacket : %s:%d to %s:%d flag :%s - seq = %d - ack = %d - dataLength = %d",
                        pk.getIPHeader().getSourceAddressName(),tcp.getDestPortIntValue(),
                        pk.getIPHeader().getDestAddressName(),tcp.getDestPortIntValue(),
                        Integer.toBinaryString(tcp.getFlag().getByte()),
                        tcp.getSequenceNumberIntValue(),tcp.getAcknowledgmentNumberIntValue(),
                        ((PacketV4) packet).getData().length));
            }
            mPacketProcessor.onNewPacket(pk);
        }
        mKeepAliveThread.resetTimer();

    }

    private  void packetAvailableToWrite(PacketV4 pk) {
        mPacketQueue.add(pk);
        writerLock.release();
        mKeepAliveThread.resetTimer();

    }

    private void startReaderThread() {
        if (mState == State.SYN_RECEIVED) {
            mRemoteReaderThread = new Thread(new ChannelReader(mChannel, this));
            mRemoteReaderThread.setName(getId() + "_reader");
            mRemoteReaderThread.start();
            mState = State.ESTABLISHED;
        }
    }

    private void sendToLocal(Packet packet) {
        if (packet instanceof PacketV4) {
            PacketV4 pk = (PacketV4) packet;
            if (pk.getTransmissionProtocol() instanceof TCP) {
                TCP tcp = (TCP) pk.getTransmissionProtocol();
                if (tcp.getFlag().SYN == 1 || tcp.getFlag().FIN == 1) {
                    mNextAcknowledgeNumber = tcp.getSequenceNumberIntValue() + 1;
                } else {
                    mNextAcknowledgeNumber = tcp.getSequenceNumberIntValue() + (pk.getData() == null ? 0 : pk.getData().length);
                }
                mNextSequenceNumber = tcp.getAcknowledgmentNumberIntValue();
                mServerSequenceNumber = mNextAcknowledgeNumber;
                mServerAcknowledgeNumber = mNextSequenceNumber;
            }
            if (pk.getIPHeader().getSourceAddressName().equals("88.99.231.246")){
                TCP tcp = (TCP) pk.getTransmissionProtocol();
                Log.v("TTT",String.format("sendToLocal : %s:%d to %s:%d flag :%s - seq = %d - ack = %d - dataLength = %d",
                        pk.getIPHeader().getSourceAddressName(),tcp.getDestPortIntValue(),
                        pk.getIPHeader().getDestAddressName(),tcp.getDestPortIntValue(),
                        Integer.toBinaryString(tcp.getFlag().getByte()),
                        tcp.getSequenceNumberIntValue(),tcp.getAcknowledgmentNumberIntValue(),
                        ((PacketV4) packet).getData().length));
            }
        }
        mChannelManager.sendToLocal(packet);
    }

    private void sendACKToClient() {

        Packet packet = makeTcpPacket(null, TCPFlag.ACKOnly());
        mKeepAliveThread.resetTimer();
        sendToLocal(packet);

    }

    private void sendFINPacket() {
        if (mState == State.ESTABLISHED) {
            mState = State.FIN_WAIT_1;
        } else {
            mState = State.LAST_ACK;
        }
        Packet packet = makeTcpPacket(null, TCPFlag.FIN_ACK());
        mKeepAliveThread.resetTimer();
        sendToLocal(packet);
    }

    private void handshake() {
        mServerSequenceNumber = new Random().nextInt(Integer.MAX_VALUE / 2);
        mServerAcknowledgeNumber = ((TCP) mInitialPacket.getTransmissionProtocol()).getSequenceNumberIntValue() + 1;
        mState = State.SYN_RECEIVED;
        Packet packet = makeTcpPacket(null, TCPFlag.SYN_ACK());
        mKeepAliveThread.resetTimer();
        sendToLocal(packet);
    }

    private void sendCloseConnectionToClient() {

        Packet closePacket = makeTcpPacket(null, TCPFlag.RST_ACK());
        mKeepAliveThread.resetTimer();
        sendToLocal(closePacket);

    }

    private void sendKeepAlivePacket() {
        // first we increase nextSeqNumber to generate packet
        mNextAcknowledgeNumber = mNextAcknowledgeNumber - 1;
        Packet closePacket = makeTcpPacket(null, TCPFlag.RST_ACK());
        // after generate packet , set nextSeqNumber to its real value
        mNextAcknowledgeNumber = mNextAcknowledgeNumber + 1;
        sendToLocal(closePacket);
    }

    private  void sendDataToClient(byte[] data, boolean PSH) {

        TCPFlag flag = PSH ? TCPFlag.ACKOnly() : TCPFlag.PSH_ACK();
        Packet packet = makeTcpPacket(data, flag);
        mKeepAliveThread.resetTimer();
        sendToLocal(packet);

    }

    private  Packet makeTcpPacket(byte[] data, TCPFlag flag) {

        TCP tcp = new TCP(getRemotePort(), getLocalPort());
        tcp.setUrgentPointer(0);
        tcp.setWindowSize(6000);
        tcp.setFlag(flag);
        tcp.setAcknowledgmentNumber(mServerAcknowledgeNumber);
        tcp.setSequenceNumber(mServerSequenceNumber);
        int ipDataLength = tcp.getHeaderLength() + (data == null ? 0 : data.length);
        IPV4Header header = generateHeader(getRemoteAddress(), getLocalAddress(), ipDataLength);
        return new PacketV4(header, tcp, data);
    }

    /**
     * @param ipDataLength if is TCP it is TCP header length + data length
     *                     if it is UPD it must be UPD header length + data length
     */
    private IPV4Header generateHeader(byte[] sourceAddress, byte[] destAddress, int ipDataLength) {
        IPV4Header header = new IPV4Header(sourceAddress, destAddress);
        header.setFlag(mInitialPacket.getIPHeader().getFlag());
        header.setIdentification(mInitialPacket.getIPHeader().getIdentification());
        header.setProtocol(TCP.PROTOCOL_NUMBER);
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
        // todo uncomment this later
//        mKeepAliveThread.start();
        try {
            mChannel = (ChannelDirectTCPIP) mSession.openChannel("direct-tcpip");
            mChannel.setHost(mInitialPacket.getIPHeader().getDestAddressName());
            mChannel.setPort(mInitialPacket.getTransmissionProtocol().getDestPortIntValue());
            remoteSelfOutStream = new PipedOutputStream();
            mRemoteIn = new PipedInputStream(remoteSelfOutStream, Packet.MAX_SIZE);
            mChannel.setOutputStream(remoteSelfOutStream, true);
            mRemoteOut = mChannel.getOutputStream();
            mChannel.connect(5000);

            handshake();

            while (true) {
                // wait for handshake paket first then
                // for other packet

                PacketV4 pk = mPacketQueue.poll();
                if (pk == null) {
                    writerLock.acquire();
                    continue;
                }

                TCP tcp = (TCP) pk.getTransmissionProtocol();
                if (tcp.getSequenceNumberIntValue() != mNextSequenceNumber || tcp.getAcknowledgmentNumberIntValue() != mNextAcknowledgeNumber){
                    continue;
                }
                if (mChannel.isConnected()) {

                    if (pk.getData() != null) {
                        for (int tryCount = 0; tryCount < 3; tryCount++) {
                            try {

                                mRemoteOut.write(pk.getData());
                                if (((TCP) pk.getTransmissionProtocol()).getFlag().PSH == 1) {
                                    mRemoteOut.flush();
                                    // sender need ack back
                                    setAckSeqNumber(pk);
                                    sendACKToClient();
                                } else if (((TCP) pk.getTransmissionProtocol()).getFlag().ACK == 1) {
                                    // sender has more data to send so we don't send ack
                                    setAckSeqNumber(pk);
                                }
                                readerLock.release();
                                break;
                            } catch (IOException e) {
                                if (!(e instanceof SocketTimeoutException)) {
                                    throw e;
                                }
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


        close();
    }


    private void setAckSeqNumber(PacketV4 pk) {
        TCP tcp = (TCP) pk.getTransmissionProtocol();
        mServerSequenceNumber = tcp.getAcknowledgmentNumberIntValue();
        mServerAcknowledgeNumber = tcp.getSequenceNumberIntValue() +
                (tcp.getFlag().SYN == 1 ? 1 : (pk.getData() == null ? 0 : pk.getData().length));
        mNextSequenceNumber = mServerAcknowledgeNumber;
        mNextAcknowledgeNumber = mServerSequenceNumber;
        mKeepAliveThread.resetTimer();

    }

    /**
     * After the side that sent the first FIN has responded with the final ACK,
     * it waits for a timeout before finally closing the connection,
     * during which time the local port is unavailable for new connections;
     * this state lets the TCP client resend the final acknowledgement to the server
     * in case the ACK is lost in transit. The time duration is implementation-dependent,
     * but some common values are 30 seconds, 1 minute, and 2 minutes. After the timeout,
     * the client enters the CLOSED state and the local port becomes available for new connections.
     */
    private void startCloseTimer() {
        mTimerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(30 * 1000);
                    if (mState != State.CLOSED) {
                        close();
                    }
                } catch (InterruptedException ignore) {

                }
            }
        });
        mTimerThread.start();
    }


    @Override
    public void close() {
        if (mState == State.CLOSED) {
            return;
        }
        mState = State.CLOSED;
        Log.v("TTT", "channel closed : " + String.format("from %s:%d to %s:%d flag is : %d",
                mInitialPacket.getIPHeader().getSourceAddressName(), mInitialPacket.getTransmissionProtocol().getSourcePortIntValue(),
                mInitialPacket.getIPHeader().getDestAddressName(), mInitialPacket.getTransmissionProtocol().getDestPortIntValue(),
                ((TCP) (mInitialPacket.getTransmissionProtocol())).getFlag().getByte()));


        terminate();

    }

    @Override
    public void terminate() {
        if (mState != State.CLOSED){
            sendCloseConnectionToClient();
        }
        if (mChannel != null) {
            mChannel.disconnect();
        }
        mChannelManager.removeChannel(this);
        if (mRemoteReaderThread != null) {
            mRemoteReaderThread.interrupt();
        }
        if (mPacketProcessor.isAlive()) {
            mPacketProcessor.interrupt();
        }
        if (mTimerThread != null) {
            mTimerThread.interrupt();
        }
        if (mKeepAliveThread != null) {
            mKeepAliveThread.cancel();
            mKeepAliveThread.interrupt();
        }
    }


    public static class ChannelReader implements Runnable {

        private final ChannelDirectTCPIP mChannel;
        private final ChannelV4TCP mChannelV4TCP;
        private final int MAX_BUFFER_SIZE = 4 * 1024;


        public ChannelReader(ChannelDirectTCPIP channel, ChannelV4TCP channelV4TCP) {
            mChannel = channel;
            mChannelV4TCP = channelV4TCP;

        }

        @Override
        public void run() {

            if (mChannel != null && mChannel.isConnected()) {
                try {
                    byte[] buffer = new byte[mChannelV4TCP.mMaxSegmentSize];
                    int len;
                    int bytesRead = 0;
                    boolean psh = false;
                    int remainingPushSize;
                    while (true) {
//                        readerLock.acquire();
                        if (mChannel.isConnected()) {
                            remainingPushSize = Math.min(MAX_BUFFER_SIZE - bytesRead, buffer.length);
                            len = mChannelV4TCP.mRemoteIn.read(buffer, 0, remainingPushSize);
                            if (len > 0) {
                                bytesRead += len;
                                if (len < buffer.length || bytesRead == MAX_BUFFER_SIZE) {
                                    psh = true;
                                    bytesRead = 0;
                                }
                                byte[] data = Arrays.copyOfRange(buffer, 0, len);
                                mChannelV4TCP.sendDataToClient(data, psh);
                                if (mChannelV4TCP.mRemoteIn.available() <= 0 && mChannel.isEOF()) {
                                    mChannelV4TCP.sendFINPacket();
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
                }/* catch (InterruptedException e) {
                    e.printStackTrace();
                }*/
            }

            mChannelV4TCP.close();

        }
    }

    private static class PacketProcessor extends Thread {
        private ChannelV4TCP mChannel;
        private ConcurrentLinkedQueue<PacketV4> mPacketQueue;
        /**
         * When new packet is available we process it by releasing this lock
         */
        private Semaphore mNewPacketLock;

        private PacketProcessor(ChannelV4TCP channel) {
            mChannel = channel;
            mPacketQueue = new ConcurrentLinkedQueue<>();
            mNewPacketLock = new Semaphore(0);
            setName(mChannel.getId() + "_packetProcessor");

        }

        public void onNewPacket(PacketV4 packet) {
            mPacketQueue.add(packet);
            mNewPacketLock.release();
        }

        @Override
        public void run() {
            while (true) {
                try {
                    mNewPacketLock.acquire();
                } catch (InterruptedException e) {
                    return;
                }
                if (interrupted()) {
                    return;
                }
                PacketV4 pk = mPacketQueue.poll();
                if (pk == null) {
                    continue;
                }

                if (pk.getTransmissionProtocol() instanceof TCP) {
                    TCP newPacketTCPSection = (TCP) pk.getTransmissionProtocol();
                    TCPFlag newPacketFlag = newPacketTCPSection.getFlag();

                    if (newPacketTCPSection.getAcknowledgmentNumberIntValue() != mChannel.mNextAcknowledgeNumber) {
                        continue;
                    }


                    if (newPacketFlag.SYN == 1 && newPacketFlag.ACK == 0) {
                        mChannel.mServerSequenceNumber = new Random().nextInt(Integer.MAX_VALUE / 2);
                        mChannel.mServerAcknowledgeNumber = newPacketTCPSection.getSequenceNumberIntValue() + 1;
                        mChannel.mNextSequenceNumber = mChannel.mServerAcknowledgeNumber;
                        mChannel.mNextAcknowledgeNumber = mChannel.mServerSequenceNumber;
                        mChannel.sendACKToClient();
                    } else if (newPacketFlag.SYN == 1 && newPacketFlag.ACK == 1) {
                        mChannel.setAckSeqNumber(pk);
                        mChannel.sendACKToClient();
                    } else if (newPacketFlag.RST == 1) {
                        // reset connection , close connection
                        mChannel.setAckSeqNumber(pk);
                        mChannel.close();
                    } else if (newPacketFlag.PSH == 1) {
                        // client is sending data to remote server
                        mChannel.packetAvailableToWrite(pk);
                        // write data to server then if success setAckSecNumber then send ack to client
                    } else if (newPacketFlag.FIN == 1) {
                        mChannel.setAckSeqNumber(pk);
                        if (mChannel.mState == ChannelV4TCP.State.ESTABLISHED) {
                            mChannel.mState = ChannelV4TCP.State.CLOSE_WAIT;
                            mChannel.sendACKToClient();
                            mChannel.mState = ChannelV4TCP.State.LAST_ACK;
                            mChannel.sendFINPacket();
                        } else {
                            /*In this case we must be in FIN_WAIT_2 or TIME_WAIT state
                               We waiting for time out*/
                            mChannel.mState = ChannelV4TCP.State.TIME_WAIT;
                            mChannel.sendACKToClient();
                        }

                    } else if (newPacketFlag.ACK == 1) {
                        // There is 3 options :
                        // 1. This new packet is acknowledge packet
                        // 2. This new packet is keep-alive packet
                        // 3. This ne packet is resuming of data from client

                        if (pk.getData() != null) {
                            // case 3 . So just write data to remote out then setAckSeqNumber
                            mChannel.packetAvailableToWrite(pk);
                        } else if (newPacketTCPSection.getSequenceNumberIntValue() == mChannel.mServerAcknowledgeNumber - 1) {
                            // case 2. just send ack to client
                            mChannel.sendACKToClient();
                        } else {
                            // case 1. This is acknowledge of last sending data to local. 
                            mChannel.setAckSeqNumber(pk);
                            if (mChannel.mState == ChannelV4TCP.State.FIN_WAIT_1) {
                                mChannel.mState = ChannelV4TCP.State.FIN_WAIT_2;
                                mChannel.startCloseTimer();
                            } else if (mChannel.mState == ChannelV4TCP.State.LAST_ACK) {
                                mChannel.close();
                            } else if (mChannel.mState == ChannelV4TCP.State.SYN_RECEIVED) {
                                mChannel.startReaderThread();
                            }
                        }

                    }


                }
            }
        }
    }

    private static class KeepAliveThread extends Thread {
        private boolean mStop = false;
        private ChannelV4TCP mChannel;
        private int mIntervalSecond;
        private long triggerTime;
        private boolean mKeepAliveSent = false;

        public KeepAliveThread(ChannelV4TCP channel, int intervalSecond) {
            mChannel = channel;
            mIntervalSecond = intervalSecond;
            setName(mChannel.getId() + "_keep-alive");
        }

        @Override
        public void run() {
            triggerTime = System.currentTimeMillis() + mIntervalSecond * 1000L;
            while (!mStop) {
                try {
                    long sleep = triggerTime - System.currentTimeMillis();
                    if (sleep > 0) {
                        Thread.sleep(sleep + 1);
                        continue;
                    }
                    if (mKeepAliveSent) {
                        break;
                    }
                    mChannel.sendKeepAlivePacket();
                    mKeepAliveSent = true;
                    resetTimer(false);
                } catch (InterruptedException ignore) {

                }
            }
            mChannel.terminate();
        }

        public void resetTimer() {
            resetTimer(true);
        }

        private void resetTimer(boolean resetKeepAliveSentStatus) {
            if (resetKeepAliveSentStatus) {
                mKeepAliveSent = false;
            }
            triggerTime = System.currentTimeMillis() + mIntervalSecond * 1000L;
        }

        public void cancel() {
            mStop = true;
            triggerTime = 0;
        }
    }


}
