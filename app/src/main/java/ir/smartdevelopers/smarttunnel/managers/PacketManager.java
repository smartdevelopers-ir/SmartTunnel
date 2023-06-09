package ir.smartdevelopers.smarttunnel.managers;

import java.util.concurrent.LinkedBlockingQueue;

import ir.smartdevelopers.smarttunnel.packet.IPV4Header;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.packet.PacketV4;
import ir.smartdevelopers.smarttunnel.packet.TCP;
import ir.smartdevelopers.smarttunnel.packet.TransmissionProtocol;
import ir.smartdevelopers.smarttunnel.packet.TransmissionProtocolFactory;
import ir.smartdevelopers.smarttunnel.packet.UDP;
import ir.smartdevelopers.smarttunnel.utils.Logger;

/**
 * We need find out what kind of paket must be send to remote server
 * This class checks packet version and makes {@link ir.smartdevelopers.smarttunnel.packet.Packet}
 * from incoming bytes and sends this Packets to {@link PacketManager}
 * */
public class PacketManager {


    /** We write to local client in this thread*/
    private  Thread mWriterThread;
    private final ChannelManager mChannelManager;
    private final LinkedBlockingQueue<Packet> mPacketsQueue;
    private final LocalPacketWriter mLocalPacketWriter;

    private boolean mDestroyed;
    private ServerPacketListener mServerPacketListener;
//    private final Object writeLock = new Object();


    public PacketManager(ServerPacketListener serverPacketListener, ChannelManager channelManager)  {
        mPacketsQueue = new LinkedBlockingQueue<>();
        mChannelManager = channelManager;
        mChannelManager.setPacketManager(this);
        mServerPacketListener = serverPacketListener;
        mLocalPacketWriter = new LocalPacketWriter(mPacketsQueue,mServerPacketListener, this);
        mWriterThread = new Thread(mLocalPacketWriter,"packetManagerLocalWriter");
        mWriterThread.start();
    }


    public void writeToLocal(Packet packet){

       mPacketsQueue.add(packet);
       Logger.logDebug("PacketManager mPacketsQueue size is :" + mPacketsQueue.size());
//       mLocalPacketWriter.dataAvailable();
    }


    static class LocalPacketWriter implements Runnable{
        private final LinkedBlockingQueue<Packet> mPacketsQueue;
        private final ServerPacketListener mServerPacketListener;
//        private final Semaphore mWriterLock;
        private final PacketManager mPacketManager;

        public LocalPacketWriter(LinkedBlockingQueue<Packet> packetsQueue, ServerPacketListener serverPacketListener,
                                 PacketManager packetManager) {
            mPacketsQueue = packetsQueue;
            mServerPacketListener = serverPacketListener;
            mPacketManager = packetManager;
//            mWriterLock = new Semaphore(0);
        }

        @Override
        public void run() {
            try {
                while (true){
                    if (mPacketManager.mDestroyed){
                        break;
                    }
                    Packet packet =mPacketsQueue.take();
                    if (packet != null){
                        mServerPacketListener.onPacketFromServer(packet);
                    }
                }
            } catch (InterruptedException e) {
//                if (!mPacketManager.mDestroyed){
//                    mPacketManager.mWriterThread = new Thread(this);
//                    mPacketManager.mWriterThread.start();
//                }
            }
        }

//        public void dataAvailable() {
//            mWriterLock.release();
//        }
    }
    /** We read incoming bytes from tun, process them, and create proportionate Packet object
     * and send them to remote server */
    public void sendToRemoteServer(byte[] data){
        Packet packet = getPacket(data);
        if (packet ==null ){
            return;
        }
//        if (packet instanceof PacketV4){
//            if (!((PacketV4) packet).getIPHeader().getDestAddressName().equals("151.245.16.92")){
//                return;
//            }
//        }
//       if (packet.getProtocolNumber() != UDP.PROTOCOL_NUMBER){
//           continue;
//       }
//       if (!Arrays.equals(packet.getDestAddress(),new byte[]{5,45,64,41})){
//           continue;
//       }
//       PacketV4 pk = (PacketV4) packet;
//       IPV4Header header = IPV4Header.fromHeaderByte(pk.getIPHeader().getHeader());
//       header.setDestAddress(pk.getIPHeader().getSourceAddress());
//       header.setSourceAddress(pk.getDestAddress());
//       header.setHeaderChecksum(new byte[]{0,0});
//       byte[] data = {-10, -31, -127, -128, 0, 1, 0, 1, 0, 0, 0, 0, 2, 115, 51, 6, 103, 111, 111, 108, 104, 97, 2, 116, 107, 0, 0, 1, 0, 1, -64, 12, 0, 1, 0, 1, 0, 0, 1, 44, 0, 4, 5, 45, 64, 41};
//       ArrayUtil.replace(data,0,Arrays.copyOfRange(pk.getData(),0,2));//replace dns id
//       UDP udp = new UDP(pk.getDestPort(), pk.getSourcePort());
//
//       PacketV4 v4 = new PacketV4(header,udp,data);
//       mPacketManager.writeToLocal(v4);

        mChannelManager.sendToRemoteServer(packet);
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

    public  void destroy(){
        mDestroyed = true;
        if (mWriterThread != null){
            mWriterThread.interrupt();
        }
        if (mChannelManager != null) {
            mChannelManager.destroy();
        }
    }
    public interface ServerPacketListener {
        void onPacketFromServer(Packet packet);
    }
}
