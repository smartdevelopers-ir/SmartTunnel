package ir.smartdevelopers.smarttunnel.channels;

import android.util.Log;

import com.jcraft.jsch.ChannelDirectTCPIP;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Random;
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
    private InputStream mRemoteIn;
    private OutputStream mRemoteOut;
    private Thread mRemoteReaderThread;
    private ChannelDirectTCPIP mChannel;
    private final ChannelManager mChannelManager;
    private PacketV4 mLastClientPacket;
    private Semaphore writerLock;
    /**We must write to remote out first then reade from remote input
     * so before we reade we must wait ro writer to release this lock*/
    private Semaphore readerLock;

    /** Last seqNumber that sent to local client*/
    private int mServerSequenceNumber;
    /** Last ackNumber that sent to local client*/
    private int mServerAcknowledgeNumber;
    /** Length of last data bytes that sent to local client*/
    private int mServerDataLength;



    public ChannelV4TCP(PacketV4 packetV4, Session session, ChannelManager channelManager) {
        super(packetV4.getTransmissionProtocol().getSourcePort()
                , packetV4.getTransmissionProtocol().getDestPort()
                , packetV4.getIPHeader().getSourceAddress()
                , packetV4.getIPHeader().getDestAddress());

        mSession = session;
        mChannelManager = channelManager;
        mLastClientPacket = packetV4;
        writerLock = new Semaphore(0);
        readerLock = new Semaphore(0);
    }

    @Override
    public synchronized void onNewPacket(Packet packet) {

        if (packet instanceof  PacketV4){
            PacketV4 pk = (PacketV4) packet;
            if (pk.getTransmissionProtocol() instanceof TCP) {
                TCP newPacketTCPSection = (TCP) pk.getTransmissionProtocol();
                TCPFlag lastPacketFlag = ((TCP) (mLastClientPacket.getTransmissionProtocol())).getFlag();
                TCPFlag newPacketFlag = newPacketTCPSection.getFlag();

                if (lastPacketFlag.SYN == 1 ){ // this is handshake packet

                    if (newPacketTCPSection.getAcknowledgmentNumberIntValue() == mServerSequenceNumber + 1){
                        // client sent last ACK to establishing TCP connection
                        mLastClientPacket = pk;
                        startReaderThread();
                    }else {
                        close();
                    }
                }else if (newPacketFlag.RST == 1 ){
                    // reset connection , close connection
                    mLastClientPacket = pk;
                    close();
                } else if (newPacketFlag.PSH == 1) {
                    // client is sending data to remote server
                    mLastClientPacket = pk;
                    writerLock.release();
                } else if (newPacketFlag.FIN == 1) {
                    // todo : complete this
                } else if (newPacketFlag.ACK == 1) {
                    // There is 2 options :
                    // 1. This new packet is acknowledge packet
                    // 2. This new packet is keep-alive packet

                    if (newPacketTCPSection.getSequenceNumberIntValue() == mServerAcknowledgeNumber &&
                    newPacketTCPSection.getAcknowledgmentNumberIntValue() == (mServerSequenceNumber + mServerDataLength)){
                        // This new packet is acknowledge of last sent data to local client
                        mLastClientPacket = pk;
                    }else if (newPacketTCPSection.getSequenceNumberIntValue() == (mServerAcknowledgeNumber - 1) ){
                        // This new packet is keep-alive packet from client
                        // We have last right packet so we don't need set mLastPacket = pk
                        // Just acknowledge it
                        sendACKToClient();
                    }else {
                        close();
                    }
                }


            }
        }

    }

    private void startReaderThread(){
        mRemoteReaderThread = new Thread(new ChannelReader(mChannel, this,readerLock));
        mRemoteReaderThread.start();
    }
    private void sendToLocal(Packet packet){
        if (packet instanceof PacketV4){
            PacketV4 pk = (PacketV4) packet;
            if (pk.getTransmissionProtocol() instanceof TCP){
                mServerAcknowledgeNumber = ((TCP) pk.getTransmissionProtocol()).getAcknowledgmentNumberIntValue();
                mServerSequenceNumber = ((TCP) pk.getTransmissionProtocol()).getSequenceNumberIntValue();
                mServerDataLength = pk.getData() == null ? 0 : pk.getData().length;
            }
        }
        mChannelManager.sendToLocal(packet);
    }
    private void sendACKToClient(){

        if (mLastClientPacket.getTransmissionProtocol() instanceof TCP){

            Packet packet = makeTcpPacket(null,getRemoteAddress(),getLocalAddress(),
                    getRemotePort(),getLocalPort(),
                    TCPFlag.ACKOnly(),calculateClientSequenceNumber(),calculateClientAcknowledgeNumber());
            sendToLocal(packet);
        }

    }

    private void handshake() {
        Packet packet = makeTcpPacket(null, getRemoteAddress(), getLocalAddress(),
                getRemotePort(), getLocalPort(),
                TCPFlag.SYN_ACK(), new Random().nextInt(), calculateClientAcknowledgeNumber());
        sendToLocal(packet);
    }

    private synchronized void sendDataToClient(byte[] data) {

        Packet packet = null;
        if (mLastClientPacket.getTransmissionProtocol() instanceof TCP){
            packet = makeTcpPacket(data, getRemoteAddress(), getLocalAddress(),
                    getRemotePort(), getLocalPort(),
                    TCPFlag.PSH_ACK(), calculateClientSequenceNumber(), calculateClientAcknowledgeNumber());
        }
        if (packet!=null) {
            sendToLocal(packet);
        }
    }
    private int calculateClientAcknowledgeNumber(){
        if (mLastClientPacket.getTransmissionProtocol() instanceof TCP){
            TCP lastTCPSection = (TCP) mLastClientPacket.getTransmissionProtocol();
            return lastTCPSection.getSequenceNumberIntValue() + (lastTCPSection.getFlag().SYN == 1 ? 1 :
                    (mLastClientPacket.getData() == null ? 0 : mLastClientPacket.getData().length));
        }
        return 0;
    }
    private int calculateClientSequenceNumber(){
        if (mLastClientPacket.getTransmissionProtocol() instanceof TCP){
            TCP lastTCPPacket = (TCP) mLastClientPacket.getTransmissionProtocol();
            return lastTCPPacket.getAcknowledgmentNumberIntValue();
        }
        return 0;
    }

    private synchronized Packet makeTcpPacket(byte[] data, byte[] sourceAddress, byte[] destAddress,
                                              byte[] sourcePort, byte[] destPort, TCPFlag flag,
                                              int sequenceNumber, int acknowledgeNumber) {

        TCP tcp = new TCP(sourcePort, destPort);
        tcp.setUrgentPointer(0);
        tcp.setWindowSize(6000);
        tcp.setFlag(flag);
        tcp.setAcknowledgmentNumber(acknowledgeNumber);
        tcp.setSequenceNumber(sequenceNumber);
        int ipDataLength = tcp.getHeaderLength() + (data == null ? 0 : data.length);
        IPV4Header header = generateHeader(sourceAddress,destAddress,ipDataLength);
        return new PacketV4(header, tcp, data);
    }

    /**@param ipDataLength if is TCP it is TCP header length + data length
     * if it is UPD it must be UPD header length + data length
     * */
    private IPV4Header generateHeader(byte[] sourceAddress, byte[] destAddress,int ipDataLength){
        IPV4Header header = new IPV4Header(sourceAddress, destAddress);
        header.setFlag(mLastClientPacket.getIPHeader().getFlag());
        header.setIdentification(mLastClientPacket.getIPHeader().getIdentification());
        header.setProtocol(TCP.PROTOCOL_NUMBER);
        header.setTimeToLive((byte) 30);
        header.setFragmentOffset(mLastClientPacket.getIPHeader().getFragmentOffset());
        header.setTypeOfService(mLastClientPacket.getIPHeader().getTypeOfService());
        header.setTotalLength(ByteUtil.getByteFromInt(header.getHeaderLength() + ipDataLength, 2));
        return header;
    }

    @Override
    public void run() {
        if (!mSession.isConnected()) {
            try {
                mSession.connect();
            } catch (JSchException e) {
                Log.e("TTT", "run: ", e);
            }
        }
            try {
                mChannel = (ChannelDirectTCPIP) mSession.openChannel("direct-tcpip");
                mChannel.setHost(mLastClientPacket.getIPHeader().getDestAddressName());
                mChannel.setPort(mLastClientPacket.getTransmissionProtocol().getDestPortIntValue());
                mChannel.connect(5000);

                handshake();

                mRemoteOut = mChannel.getOutputStream();
                while (true){
                    // wait for handshake paket first then
                    // for other packet
                    writerLock.acquire();
                    if (mChannel.isConnected()){
                        if (mLastClientPacket.getData() != null){
                            for (int tryCount =0 ; tryCount < 3 ; tryCount ++){
                                try {

                                    mRemoteOut.write(mLastClientPacket.getData());
                                    mRemoteOut.flush();
                                    readerLock.release();
                                    break;
                                }catch (IOException e){
                                    if (!(e instanceof SocketTimeoutException)) {
                                        throw e;
                                    }
                                }
                            }

                            sendACKToClient();
                        }


                    }else {
                        break;
                    }
                }

            } catch (Exception e) {
                Logger.logDebug(e.getMessage());
            }


        close();
    }



    private void sendCloseConnectionToClient() {
        if (mLastClientPacket.getTransmissionProtocol() instanceof TCP){
            Packet closePacket = makeTcpPacket(null,getRemoteAddress(),getLocalAddress(),
                    getRemotePort(),getLocalPort(),
                    TCPFlag.RST_ACK(),calculateClientSequenceNumber(),calculateClientAcknowledgeNumber());
            sendToLocal(closePacket);
        }
    }

    @Override
    public void close() {
        if (mChannel != null) {
            mChannel.disconnect();
        }
        mChannelManager.removeChannel(this);
        sendCloseConnectionToClient();
        if (mRemoteReaderThread != null){
            mRemoteReaderThread.interrupt();
        }
        Thread current = Thread.currentThread();
        current.interrupt();
    }


    public static class ChannelReader implements Runnable {

        private final ChannelDirectTCPIP mChannel;
        private final ChannelV4TCP mChannelV4TCP;
        private Semaphore readerLock;

        public ChannelReader(ChannelDirectTCPIP channel, ChannelV4TCP channelV4TCP, Semaphore readerLock) {
            mChannel = channel;
            mChannelV4TCP = channelV4TCP;
            this.readerLock = readerLock;
        }

        @Override
        public void run() {

            if (mChannel != null && mChannel.isConnected()) {
                try (InputStream chanelIn = mChannel.getInputStream()) {
                    byte[] buffer = new byte[Packet.MAX_SIZE];
                    int len;
                    while (true) {
                        readerLock.acquire();
                        if (mChannel.isConnected()) {
                            len = chanelIn.read(buffer);
                            if (len > 0) {
                                mChannelV4TCP.sendDataToClient(Arrays.copyOfRange(buffer, 0, len));
                            } else {
                                break;
                            }

                        } else {
                            break;
                        }
                    }
                } catch (IOException e) {
                    Logger.logDebug(e.getMessage());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            mChannelV4TCP.sendCloseConnectionToClient();

        }
    }


}
