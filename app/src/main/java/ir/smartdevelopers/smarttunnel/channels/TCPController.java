package ir.smartdevelopers.smarttunnel.channels;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.packet.PacketV4;
import ir.smartdevelopers.smarttunnel.packet.TCP;
import ir.smartdevelopers.smarttunnel.packet.TCPFlag;
import ir.smartdevelopers.smarttunnel.packet.TCPOption;
import ir.smartdevelopers.smarttunnel.packet.TCPPacketQueue;
import ir.smartdevelopers.smarttunnel.packet.TCPPacketWrapper;

import static ir.smartdevelopers.smarttunnel.packet.TCP.*;

public class TCPController {
    private static final int SERVER_MAX_SEGMENT_SIZE = 1400;
    /** Every packets that received from client will add in this queue
     * then remote writer thread poll from this abd sends to remote*/
    private final TCPPacketQueue mRemotePacketQueue ;
    /** Every packet that comes from remote must add to this and waits for acknowledge
     * from client to remove*/
    private final LinkedList<TCPPacketWrapper> mClientPackets;
    private final Packet.PacketCreator mPacketCreator;
    private final Packet mInitialPacket;
    /** TCP state*/
    private TCP.State mState;
    private int mClientSeqNumber;
    private int mClientNexSeqNumber;
    private int mRemoteSeqNumber;
//    private int mRemoteNexSeqNumber;
    /** Maximum tcp data size that client can buffer */
    private int mMaxSegmentSize;
    private int mClientWindowSize;
    private int mClientWindowSizeScale;
    private int mServerWindowSize = 65535;
    private int mServerWindowSizeScale = 8;
    /** This is a name for indicating the controller for debugging and
     * Threads name */
    private final String mName;
    private final Object mWindowFullLock = new Object();
    private Thread mTimerThread;
    private final KeepAliveThread mKeepAliveThread;
    /**
     * This processor process new packet in another thread
     */
    private final PacketProcessor mPacketProcessor;

    private final TcpListener mTcpListener;


    public TCPController(Packet initialPacket, Packet.PacketCreator packetCreator,
                         String name, TcpListener tcpListener) {
        mPacketCreator = packetCreator;
        this.mName = name;
        this.mInitialPacket = initialPacket;
        mTcpListener = tcpListener;
        mRemotePacketQueue = new TCPPacketQueue();
        mClientPackets = new LinkedList<>();
        if (initialPacket.getTransmissionProtocol() instanceof TCP){
            TCP tcp = (TCP) initialPacket.getTransmissionProtocol();
            mClientSeqNumber = tcp.getSequenceNumberIntValue();
            mClientNexSeqNumber = mClientSeqNumber + 1;
            mRemoteSeqNumber = new Random().nextInt();
//            mRemoteNexSeqNumber = mRemoteSeqNumber +1;


            mState = State.LISTEN;
            mClientWindowSize = tcp.getWindowSize();
            if (tcp.getTCPOption() != null){
                mMaxSegmentSize = tcp.getTCPOption().getMaximumSegmentSize();
                if (tcp.getTCPOption().getWindowScale() != 0){
                    mClientWindowSizeScale = 1 << tcp.getTCPOption().getWindowScale();
                }
            }

        }

        mPacketProcessor = new PacketProcessor();
        mPacketProcessor.start();
        mKeepAliveThread = new KeepAliveThread(45, mName, new KeepAliveThread.KeepAliveWorker() {
            @Override
            public void doWork() {
                sendKeepAlivePacket();
            }

            @Override
            public void onTimeOut() {
                mTcpListener.onTermination();
            }
        });
    }

    public void packetFromClient(Packet packet){
        mPacketProcessor.onNewPacket(packet);
    }
    public void packetFromRemote(byte[] data , boolean push){
        sendDataToClient(data,push);
    }
    /** When tcp-direc channel connects we must call this method*/
    public void onChannelConnected(){
//        mKeepAliveThread.start();
        handshake();
    }
    public int getMaxSegmentSize(){
        return mMaxSegmentSize;
    }

    private void sendACKToClient(TCPOption option) {

        Packet packet = makeTcpPacket(null, TCPFlag.ACKOnly(),option);
        mKeepAliveThread.resetTimer();
        sendToLocal(packet);

    }

    private void sendFINPacket() {
        if (mState == State.ESTABLISHED) {
            mState = State.FIN_WAIT_1;
        } else {
            mState = State.LAST_ACK;
        }
        byte[] data=null;
        TCPFlag flag = TCPFlag.FIN_ACK();
        if (mClientPackets.size()>0){
            data=mClientPackets.getLast().getPacket().getData();
            flag.PSH = 1;
        }
        Packet packet = makeTcpPacket(data, flag,null);
        mKeepAliveThread.resetTimer();
        sendToLocal(packet);
    }

    private void handshake() {

        mState = State.SYN_RECEIVED;
        TCPOption option = new TCPOption();
        option.setSelectiveAcknowledgmentPermitted(true);
        option.setWindowScale((byte) mServerWindowSizeScale);
        option.setMaximumSegmentSize(SERVER_MAX_SEGMENT_SIZE);
        Packet packet = makeTcpPacket(null, TCPFlag.SYN_ACK(),option);
        mKeepAliveThread.resetTimer();
        sendToLocal(packet);
    }

    private void sendCloseConnectionToClient() {

        byte[] data=null;
        TCPFlag flag = TCPFlag.RST_ACK();
        if (mClientPackets.size()>0){
            data=mClientPackets.getLast().getPacket().getData();
            flag.PSH = 1;
        }
        Packet closePacket = makeTcpPacket(data, flag,null);
        mKeepAliveThread.resetTimer();
        sendToLocal(closePacket);

    }

    private void sendKeepAlivePacket() {
        // first we increase nextSeqNumber to generate packet
        mRemoteSeqNumber -= 1;
        Packet keepAlivePacket = makeTcpPacket(new byte[]{0}, TCPFlag.ACKOnly(),null);
        sendToLocal(keepAlivePacket);
    }

    private  void sendDataToClient(byte[] data, boolean PSH) {

        TCPFlag flag = PSH ? TCPFlag.ACKOnly() : TCPFlag.PSH_ACK();
        Packet packet = makeTcpPacket(data, flag,null);
        mKeepAliveThread.resetTimer();
        sendToLocal(packet);

    }
    private void sendToLocal(Packet packet) {
        int nextSeqNumber = 0;
        if (packet instanceof PacketV4) {
            PacketV4 pk = (PacketV4) packet;
            if (pk.getTransmissionProtocol() instanceof TCP) {
                TCP tcp = (TCP) pk.getTransmissionProtocol();

                if (tcp.getFlag().SYN == 1 || tcp.getFlag().FIN == 1) {
                    nextSeqNumber = tcp.getSequenceNumberIntValue() + 1;
                } else {
                    nextSeqNumber = tcp.getSequenceNumberIntValue() + (pk.getData() == null ? 0 : pk.getData().length);
                }
            }

        }
        if (packet.getData() != null){
            // put sending packet in queue to get ack for it
            // when we get ack for packet we will remove them
            TCPPacketWrapper packetWrapper = new TCPPacketWrapper(packet);
            packetWrapper.setSequenceNumber(mRemoteSeqNumber);
            packetWrapper.setNextSequenceNumber(nextSeqNumber);
            packetWrapper.setAcknowledgeNumber(mClientNexSeqNumber);
            mClientPackets.add(packetWrapper);
        }
        mRemoteSeqNumber = nextSeqNumber;
        mTcpListener.onPacketReadyForClient(packet);
    }
    private  Packet makeTcpPacket(byte[] data, TCPFlag flag, TCPOption option) {

        TCP tcp = new TCP(mInitialPacket.getDestPort(), mInitialPacket.getSourcePort());
        tcp.setUrgentPointer(0);
        long totalWindowSize = (long) mServerWindowSize * mServerWindowSizeScale;
        long totalRemainingPacketDataSize = 0;
        for (int i =0 ;i<mRemotePacketQueue.size();i++){
            TCPPacketWrapper pk = mRemotePacketQueue.peek();
            if (pk != null){
                if (pk.getPacket() != null){
                    if (pk.getPacket().getData() != null){
                        totalRemainingPacketDataSize += pk.getPacket().getData().length;
                    }
                }
            }
        }
        totalWindowSize = totalWindowSize - totalRemainingPacketDataSize;
        tcp.setWindowSize((int) (totalWindowSize / mServerWindowSizeScale));
        tcp.setFlag(flag);
        tcp.setAcknowledgmentNumber(mClientNexSeqNumber);
        tcp.setSequenceNumber(mRemoteSeqNumber);
        tcp.setTCPOption(option);
        return mPacketCreator.create(tcp,data);
    }
    private void updateClientSeqNumber(Packet pk) {
        TCP tcp = (TCP) pk.getTransmissionProtocol();
        mClientSeqNumber = tcp.getSequenceNumberIntValue();
        mClientNexSeqNumber = mClientSeqNumber +
                (pk.getData() != null ? pk.getData().length : ((tcp.getFlag().SYN ==1 || tcp.getFlag().FIN ==1) ? 1 : 0));
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
                    Thread.sleep(60 * 1000);
                    if (mState != State.CLOSED) {
                        mTcpListener.onTermination();
                    }
                } catch (InterruptedException ignore) {

                }
            }
        });
        mTimerThread.start();
    }

    public TCPPacketQueue getRemotePacketQueue() {
        return mRemotePacketQueue;
    }

    public void terminate(){
        sendCloseConnectionToClient();
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

    public void close() {
        sendFINPacket();
    }

    public void waitIfWindowIsFull() {
        synchronized (mWindowFullLock){
            if (mClientWindowSize == 0){
                try {
                    mWindowFullLock.wait(45000);
                    if (mClientWindowSize == 0){
                        waitIfWindowIsFull();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private class PacketProcessor extends Thread {
        private ConcurrentLinkedQueue<Packet> mPacketQueue;
        /**
         * When new packet is available we process it by releasing this lock
         */
        private Semaphore mNewPacketLock;

        private PacketProcessor() {
            mPacketQueue = new ConcurrentLinkedQueue<>();
            mNewPacketLock = new Semaphore(0);
            setName(mName + "_packetProcessor");

        }

        public void onNewPacket(Packet packet) {
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
                Packet pk = mPacketQueue.poll();
                if (pk == null) {
                    continue;
                }

                if (pk.getTransmissionProtocol() instanceof TCP) {
                    TCP newPacketTCPSection = (TCP) pk.getTransmissionProtocol();
                    TCPFlag newPacketFlag = newPacketTCPSection.getFlag();
                    if (newPacketFlag.SYN == 1 && newPacketFlag.ACK == 0){
                        continue;
                    }

                    mClientWindowSize = newPacketTCPSection.getWindowSize();
                    if (mClientWindowSize > 0){
                        synchronized (mWindowFullLock){
                            mWindowFullLock.notify();
                        }
                    }
                    if(newPacketTCPSection.getSequenceNumberIntValue() != mClientNexSeqNumber){

                        if (pk.getData() != null){
                            if (newPacketTCPSection.getSequenceNumberIntValue() == mClientNexSeqNumber - 1){
                                // this is keep-alive packet
                                TCPOption.SACK[] sacks = new TCPOption.SACK[1];
                                TCPOption.SACK sack = new TCPOption.SACK();
                                sack.leftEdge = newPacketTCPSection.getSequenceNumberIntValue();
                                sack.rightEdge = newPacketTCPSection.getSequenceNumberIntValue() + pk.getData().length;
                                sacks[0] = sack;
                                TCPOption option = new TCPOption();
                                option.setSelectiveAcknowledgment(sacks);
                                sendACKToClient(option);
                            }else if (newPacketTCPSection.getSequenceNumberIntValue() > mClientNexSeqNumber){
                                TCPPacketWrapper wrapper = createPacketWrapper(pk,newPacketTCPSection.getSequenceNumberIntValue(),
                                        newPacketTCPSection.getSequenceNumberIntValue() + pk.getData().length,
                                        newPacketTCPSection.getAcknowledgmentNumberIntValue(),
                                        newPacketFlag.PSH == 1);
                                putToQueue(wrapper);
                                TCPOption.SACK[] sacks = generateSacks();
                                TCPOption option = new TCPOption();
                                option.setSelectiveAcknowledgment(sacks);
                                sendACKToClient(option);
                                continue;
                            }else {
                                continue;
                            }
                        }else if (newPacketFlag.RST == 1){
                            mTcpListener.onTermination();
                        }

                    }


                     if (newPacketFlag.SYN == 1 && newPacketFlag.ACK == 1) {
                         updateClientSeqNumber(pk);
                         TCPOption option = new TCPOption();
                         option.setMaximumSegmentSize(SERVER_MAX_SEGMENT_SIZE);
                         option.setWindowScale((byte) mServerWindowSizeScale);
                         option.setSelectiveAcknowledgmentPermitted(true);
                        sendACKToClient(option);
                    } if (newPacketFlag.PSH == 1) {
                        // client is sending data to remote server
                         TCPPacketWrapper wrapper = createPacketWrapper(pk,newPacketTCPSection.getSequenceNumberIntValue(),
                                 newPacketTCPSection.getSequenceNumberIntValue() +
                                         (pk.getData() != null ? pk.getData().length : 0),
                                 newPacketTCPSection.getAcknowledgmentNumberIntValue(),true);
                         putToQueue(wrapper);
                         updateClientSeqNumber(pk);
                    } else if (newPacketFlag.ACK == 1 && newPacketFlag.FIN == 0 && newPacketFlag.RST == 0) {
                        // There is 3 options :
                        // 1. This new packet is acknowledge packet
                        // 3. This new packet is resuming of data from client

                        if (pk.getData() != null) {
                            // case 3 . So just write data to remote out then setAckSeqNumber
                            TCPPacketWrapper wrapper = createPacketWrapper(pk,
                                    newPacketTCPSection.getSequenceNumberIntValue(),
                                    newPacketTCPSection.getSequenceNumberIntValue() + pk.getData().length,
                                    newPacketTCPSection.getAcknowledgmentNumberIntValue(),false);
                            putToQueue(wrapper);
                            updateClientSeqNumber(pk);
                        }  else {
                            // case 1. This is acknowledge of last sending data to local.
                           if (mState == TCP.State.ESTABLISHED){
                               if (newPacketTCPSection.getTCPOption() != null){
                                   TCPOption.SACK[] sacks = newPacketTCPSection.getTCPOption().getSelectiveAcknowledgmentIntValues();
                                   if (sacks != null){
                                       resendPacketsToClient(sacks,newPacketTCPSection.getAcknowledgmentNumberIntValue());
                                   }
                               }else {
                                   removeAcknowledgedPackets(newPacketTCPSection.getAcknowledgmentNumberIntValue());
                               }
                           }else if (mState == TCP.State.FIN_WAIT_1) {
                                mState = TCP.State.FIN_WAIT_2;
                                startCloseTimer();
                            } else if (mState == TCP.State.LAST_ACK) {
                                mTcpListener.onTermination();
                            } else if (mState == TCP.State.SYN_RECEIVED) {
                                mTcpListener.onConnectionEstablished();
                                mState = TCP.State.ESTABLISHED;
                            }
                        }

                    }
                     if (newPacketFlag.RST == 1) {
                        // reset connection , close connection
                        mTcpListener.onTermination();
                    }
                    if (newPacketFlag.FIN == 1) {
                         updateClientSeqNumber(pk);
                         if (mState == TCP.State.ESTABLISHED) {
                             mState = TCP.State.CLOSE_WAIT;
                             sendACKToClient(null);
                             mState = TCP.State.LAST_ACK;
                             sendFINPacket();
                         } else {
                            /*In this case we must be in FIN_WAIT_2 or TIME_WAIT state
                               We waiting for time out*/
                             mState = TCP.State.TIME_WAIT;
                             sendACKToClient(null);
                         }

                     }

                }
            }
        }
    }

    private TCPOption.SACK[] generateSacks() {
        ArrayList<TCPOption.SACK> sacks = new ArrayList<>();
        synchronized (mRemotePacketQueue){
            int i = 0;
            boolean findLeftEdge = false;
            boolean findRightEdge = false;
            int leftEdge = 0;
            int rightEdge = 0;
            while (i < mRemotePacketQueue.size()){
                TCPPacketWrapper tw = mRemotePacketQueue.get(i);
                if (tw == null){
                    break;
                }

                if (tw.getPacket() == null){
                    if (!findLeftEdge){
                        findLeftEdge = true;
                        // find for next not null packet and set its seq number
                        // as left edge
                    }else if (findRightEdge){
                        // set previous nextSeqNum as right edge
                        rightEdge = mRemotePacketQueue.get(i-1).getNextSequenceNumber();
                        TCPOption.SACK sack = new TCPOption.SACK();
                        sack.leftEdge = leftEdge;
                        sack.rightEdge = rightEdge;
                        sacks.add(sack);
                        findLeftEdge = false;
                        findRightEdge = false;
                    }
                }else {
                    if (findLeftEdge){
                        leftEdge = mRemotePacketQueue.get(i).getNextSequenceNumber();
                        findRightEdge = true;
                    }
                }
                i++;
            }
        }
        return sacks.toArray(new TCPOption.SACK[0]);
    }

    private void removeAcknowledgedPackets(int acknowledgmentNumber) {
        int removeIndex = -1;
        synchronized (mClientPackets){
            for (int i =0;i<mClientPackets.size() ;i++){
                if (mClientPackets.get(i).getNextSequenceNumber() == acknowledgmentNumber){
                    removeIndex = i;
                    break;
                }
            }
            if (removeIndex != -1){
                mClientPackets.subList(0, removeIndex + 1).clear();
            }
        }
    }

    private void resendPacketsToClient(TCPOption.SACK[] sacks, int acknowledgmentNumber) {
        List<TCPPacketWrapper> lostPackets = findLossPacket(mClientPackets,sacks,acknowledgmentNumber);
        for (TCPPacketWrapper wr : lostPackets){
            mTcpListener.onPacketReadyForClient(wr.getPacket());
        }
    }
    private List<TCPPacketWrapper> findLossPacket(List<TCPPacketWrapper> allPackets,TCPOption.SACK[] sacks, int acknowledgmentNumber){
        int sacIndex = 0;
        ArrayList<TCPPacketWrapper> resendingPackets = new ArrayList<>();
        for (int i = 0 ; i<allPackets.size(); i++){
            TCPPacketWrapper pwr = allPackets.get(i);
            TCPOption.SACK sack = sacks[sacIndex];
            if (pwr.getSequenceNumber() == acknowledgmentNumber){
                resendingPackets.add(pwr);
                if (pwr.getNextSequenceNumber() == sack.leftEdge){
                    // we sent packets up to leftEdge
                    // if there is more sack update acknowledge number to
                    // right edge of last sending packet and continue
                    acknowledgmentNumber = sack.rightEdge;
                    sacIndex++ ;
                    if (sacIndex > sacks.length){
                        break;
                    }
                }
            }
        }
        return resendingPackets;
    }

    private void putToQueue(TCPPacketWrapper wrapper) {
        mRemotePacketQueue.put(wrapper);
        TCPPacketWrapper next = createPacketWrapper(null,wrapper.getNextSequenceNumber(),0,0,false);
        mRemotePacketQueue.put(next);
    }

    private TCPPacketWrapper createPacketWrapper(Packet pk,int seqNumber,int nextSeqNumber,int ackNumber,boolean push) {
        TCPPacketWrapper wrapper = new TCPPacketWrapper(pk);
        wrapper.setSequenceNumber(seqNumber);
        wrapper.setNextSequenceNumber(nextSeqNumber);
        wrapper.setAcknowledgeNumber(ackNumber);
        wrapper.setPush(push);
        return wrapper;
    }


    public interface TcpListener{
        void onConnectionEstablished();
        void onPacketReadyForClient(Packet packet);
        void onTermination();
    }
}
