package ir.smartdevelopers.smarttunnel.managers;

import com.jcraft.jsch.Session;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import ir.smartdevelopers.smarttunnel.channels.Channel;
import ir.smartdevelopers.smarttunnel.channels.ChannelV4TCP;
import ir.smartdevelopers.smarttunnel.channels.DNSChannel;
import ir.smartdevelopers.smarttunnel.packet.IPV4Header;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.packet.PacketV4;
import ir.smartdevelopers.smarttunnel.packet.TCP;
import ir.smartdevelopers.smarttunnel.packet.UDP;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;

public class ChannelManager {
    private final Session mSession;
    private final PacketManager mPacketManager;
    private final ConcurrentHashMap<String,Channel> mChannels;
    private final ConcurrentHashMap<String,Thread> mThreads;

    public ChannelManager(Session session, PacketManager packetManager) {
        mSession = session;
        mPacketManager = packetManager;
        mChannels = new ConcurrentHashMap<>();
        mThreads = new ConcurrentHashMap<>();
    }

    public  void sendToRemoteServer(Packet packet) {

        String channelId = String.format(Locale.US,"%s_%d_%d",IPV4Header.getIPAddress(packet.getSourceAddress()),
                ByteUtil.getIntValue(packet.getSourcePort()),packet.getProtocolNumber());
        if (mChannels.get(channelId) != null){
            Objects.requireNonNull(mChannels.get(channelId)).onNewPacket(packet);
        }else {
            if (packet instanceof PacketV4){
                PacketV4 pk = (PacketV4) packet;
                Channel channel = null;
                if (pk.getTransmissionProtocol() instanceof TCP){
                    channel = new ChannelV4TCP(channelId, pk,mSession,this);
                }else if (pk.getTransmissionProtocol() instanceof UDP && ByteUtil.getIntValue(pk.getDestPort()) == 53){
                    channel = new DNSChannel(channelId,pk,mSession,this);
                }
                if (channel == null ){
                    return;
                }
                mChannels.put(channelId, channel);
                Thread t = new Thread(channel);
                t.setName(channelId);
                mThreads.put(channelId,t);
                t.start();
            }
        }
    }

    public  void removeChannel(Channel channel) {
        for (String id : mChannels.keySet()){
            if (Objects.equals(channel , mChannels.get(id))){
                mChannels.remove(id);
                Thread t = mThreads.get(id);
                if (t != null){
                    t.interrupt();
                }
                mThreads.remove(id);
                break;
            }
        }
    }

    public  void sendToLocal(Packet packet) {
        mPacketManager.writeToLocal(packet);
    }

    public void destroy() {
        if (mChannels.size() > 0) {
            for (Channel ch : mChannels.values()){
                ch.terminate();
            }
        }
    }
}
