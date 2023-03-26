package ir.smartdevelopers.smarttunnel.managers;

import com.jcraft.jsch.Session;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import ir.smartdevelopers.smarttunnel.channels.Channel;
import ir.smartdevelopers.smarttunnel.channels.ChannelV4TCP;
import ir.smartdevelopers.smarttunnel.channels.DNSChannel;
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

    public synchronized void sendToRemoteServer(Packet packet) {
        ByteBuffer idBuffer = ByteBuffer.allocate(packet.getSourceAddress().length + packet.getSourcePort().length + 1);
        idBuffer.put(packet.getSourceAddress());
        idBuffer.put(packet.getSourcePort());
        idBuffer.put(packet.getProtocolNumber());
        String channelId = ByteUtil.hash(idBuffer.array());
        if (mChannels.get(channelId) != null){
            Objects.requireNonNull(mChannels.get(channelId)).onNewPacket(packet);
        }else {
            if (packet instanceof PacketV4){
                PacketV4 pk = (PacketV4) packet;
                Channel channel = null;
                if (pk.getTransmissionProtocol() instanceof TCP){
                    channel = new ChannelV4TCP(pk,mSession,this);
                }else if (pk.getTransmissionProtocol() instanceof UDP && ByteUtil.getIntValue(pk.getDestPort()) == 53){
                    channel = new DNSChannel(pk,mSession,this);
                }
                if (channel == null ){
                    return;
                }
                mChannels.put(channelId, channel);
                Thread t = new Thread(channel);
                mThreads.put(channelId,t);
                t.start();
            }
        }
    }

    public synchronized void removeChannel(Channel channel) {
        for (String id : mChannels.keySet()){
            if (Objects.equals(channel , mChannels.get(id))){
                mChannels.remove(id);
                mThreads.remove(id);
                break;
            }
        }
    }

    public synchronized void sendToLocal(Packet packet) {
        mPacketManager.writeToLocal(packet);
    }
}
