package ir.smartdevelopers.smarttunnel.managers;

import com.jcraft.jsch.Session;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import ir.smartdevelopers.smarttunnel.packet.IPV4Header;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.packet.PacketV4;
import ir.smartdevelopers.smarttunnel.packet.TCP;
import ir.smartdevelopers.smarttunnel.packet.TCPFlag;
import ir.smartdevelopers.smarttunnel.packet.TransmissionProtocol;
import ir.smartdevelopers.smarttunnel.packet.TransmissionProtocolFactory;
import ir.smartdevelopers.smarttunnel.packet.UDP;
import ir.smartdevelopers.smarttunnel.utils.ArrayUtil;

/**
 * We need find out what kind of paket must be send to remote server
 * This class checks packet version and makes {@link ir.smartdevelopers.smarttunnel.packet.Packet}
 * from incoming bytes and sends this Packets to {@link PacketManager}
 * */
public class PacketManager {
    /**This input stream is for others to reade from this*/
    private final PipedInputStream mInputStream;
    /**This output stream is for others to write to this*/
    private final PipedOutputStream mOutputStream;
    /**This output stream is for PaketManager to write to this
     * for others to read*/
    private final PipedOutputStream mSelfOutputStream;
    /**This input stream is for PaketManager to read from this
     * that others writes*/
    private final PipedInputStream mSelfInputStream;
    /** We read from {@link #mSelfInputStream} in this thread*/
    private Thread mReaderThread;
    /** We write to {@link #mSelfOutputStream} in this thread*/
    private final Thread mWriterThread;
    private final ConcurrentLinkedQueue<Packet> mPacketsQueue;
    private final LocalPacketWriter mLocalPacketWriter;

    private Object mDataAvailableLock=new Object();
    private OnPacketFromServerListener mOnPacketFromServerListener;


    public PacketManager(Session session) throws IOException {
        mPacketsQueue = new ConcurrentLinkedQueue<>();
        ChannelManager channelManager = new ChannelManager(session, this);
        mSelfInputStream = new PipedInputStream();
        mSelfOutputStream = new PipedOutputStream();
        mInputStream = new PipedInputStream(mSelfOutputStream);
        mOutputStream = new PipedOutputStream(mSelfInputStream);
        mReaderThread = new Thread(new PacketProcessor(mSelfInputStream, channelManager,this));
        mReaderThread.start();
        mLocalPacketWriter = new LocalPacketWriter(mPacketsQueue,mSelfOutputStream);
        mWriterThread = new Thread(mLocalPacketWriter);
        mWriterThread.start();
    }

    public PipedInputStream getInputStream() {
        return mInputStream;
    }

    public PipedOutputStream getOutputStream() {
        return mOutputStream;
    }

    public synchronized void writeToLocal(Packet packet){

       mPacketsQueue.add(packet);
       mLocalPacketWriter.dataAvailable();
    }

    public void setOnPacketFromServerListener(OnPacketFromServerListener onPacketFromServerListener) {
        mOnPacketFromServerListener = onPacketFromServerListener;
    }

    static class LocalPacketWriter implements Runnable{
        private final ConcurrentLinkedQueue<Packet> mPacketsQueue;
        private final PipedOutputStream mSelfOutputStream;
        private final Semaphore mSemaphore;

        public LocalPacketWriter(ConcurrentLinkedQueue<Packet> packetsQueue, PipedOutputStream selfOutputStream) {
            mPacketsQueue = packetsQueue;
            mSelfOutputStream = selfOutputStream;
            mSemaphore = new Semaphore(0);
        }

        @Override
        public void run() {
            try {
                while (true){
                    Packet packet =mPacketsQueue.poll();
                    if (packet != null){
                        mSelfOutputStream.write(packet.getPacketBytes());
                        mSelfOutputStream.flush();
                    }else {
                        mSemaphore.acquire();
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        public void dataAvailable() {
            mSemaphore.release();
        }
    }
    /**We read incoming bytes from tun, process them, and create proportionate Packet object*/
    static class PacketProcessor implements Runnable{
        private final PipedInputStream mSelfInputStream;
        private final ChannelManager mChannelManager;
        private final byte[] buffer;
        private PacketManager mPacketManager;

        PacketProcessor(PipedInputStream selfInputStream, ChannelManager channelManager,PacketManager manager) {
            mSelfInputStream = selfInputStream;
            mChannelManager = channelManager;
            buffer = new byte[Packet.MAX_SIZE];
            mPacketManager=manager;
        }

        @Override
        public void run() {
            try{
                int len =0;
                while ((len = mSelfInputStream.read(buffer)) != -1){
                    Packet packet = getPacket(Arrays.copyOfRange(buffer,0,len));
                    if (packet ==null ){
                        continue;
                    }
//                    if (packet.getProtocolNumber() != UDP.PROTOCOL_NUMBER){
//                        continue;
//                    }
//                    if (!Arrays.equals(packet.getDestAddress(),new byte[]{5,45,64,41})){
//                        continue;
//                    }
//                    PacketV4 pk = (PacketV4) packet;
//                    IPV4Header header = IPV4Header.fromHeaderByte(pk.getIPHeader().getHeader());
//                    header.setDestAddress(pk.getIPHeader().getSourceAddress());
//                    header.setSourceAddress(pk.getDestAddress());
//                    header.setHeaderChecksum(new byte[]{0,0});
//                    byte[] data = {-10, -31, -127, -128, 0, 1, 0, 1, 0, 0, 0, 0, 2, 115, 51, 6, 103, 111, 111, 108, 104, 97, 2, 116, 107, 0, 0, 1, 0, 1, -64, 12, 0, 1, 0, 1, 0, 0, 1, 44, 0, 4, 5, 45, 64, 41};
//                    ArrayUtil.replace(data,0,Arrays.copyOfRange(pk.getData(),0,2));//replace dns id
//                    UDP udp = new UDP(pk.getDestPort(), pk.getSourcePort());
//
//                    PacketV4 v4 = new PacketV4(header,udp,data);
//                    mPacketManager.writeToLocal(v4);
                    mChannelManager.sendToRemoteServer(packet);

                }
            } catch (IOException e) {
                if (mPacketManager.mReaderThread.isInterrupted()){
                    mPacketManager.mReaderThread = new Thread(this);
                    mPacketManager.mReaderThread.start();
                }
            }
        }

        private Packet getPacket(byte[] packetBytes) {
            byte version = (byte) (packetBytes[0] >> 4);
            if (version == 4){
                return generatePacketV4(packetBytes);
            }else if (version == 6){
                // TODO: implement packetV6
                // we just return null for now
                return  null;
            }
            return null;
        }

        private Packet generatePacketV4(byte[] packetBytes) {
            IPV4Header header = IPV4Header.fromHeaderByte(packetBytes);
            // because we just accept TCP paket, for other we just return null
            // so to catch nullPointerException we surround with try/catch
            try {
                return new PacketV4(packetBytes, header, new TransmissionProtocolFactory() {
                    @Override
                    public TransmissionProtocol of(byte protocol, byte[] transmissionLayerData) {
                        if (protocol == TCP.PROTOCOL_NUMBER){
                            return TCP.fromTCPHeader(transmissionLayerData);
                        } else if (protocol == UDP.PROTOCOL_NUMBER) {
                            return UDP.fromHeaderBytes(transmissionLayerData);
                        }
                        return null;
                    }
                });
            } catch (Exception e) {
                return null;
            }
        }
    }
    public interface OnPacketFromServerListener{
        void onPacketFromServer(Packet packet);
    }
}
