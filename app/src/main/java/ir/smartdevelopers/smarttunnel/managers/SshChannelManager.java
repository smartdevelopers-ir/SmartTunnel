package ir.smartdevelopers.smarttunnel.managers;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import ir.smartdevelopers.smarttunnel.channels.Channel;
import ir.smartdevelopers.smarttunnel.channels.SshChannelV4TCP;
import ir.smartdevelopers.smarttunnel.channels.DNSChannel;
import ir.smartdevelopers.smarttunnel.channels.RemoteConnection;
import ir.smartdevelopers.smarttunnel.channels.UDPChannel;
import ir.smartdevelopers.smarttunnel.packet.IPV4Header;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.packet.PacketV4;
import ir.smartdevelopers.smarttunnel.packet.TCP;
import ir.smartdevelopers.smarttunnel.packet.UDP;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;

public class SshChannelManager extends ChannelManager {
    private final RemoteConnection mRemoteConnection;
    private final ConcurrentHashMap<String,Channel> mChannels;
    private final ConcurrentHashMap<String,Thread> mThreads;
    private int udpgwPort;

    public SshChannelManager(RemoteConnection remoteConnection,  int udpgwPort) {
        mRemoteConnection = remoteConnection;
        mChannels = new ConcurrentHashMap<>();
        mThreads = new ConcurrentHashMap<>();
        this.udpgwPort = udpgwPort;
    }

    public  void sendToRemoteServer(Packet packet) {

        String channelId ;
        if (packet.getTransmissionProtocol() instanceof UDP && ByteUtil.getIntValue(packet.getDestPort()) != 53){
            channelId = "udp_channel";
            if (udpgwPort == 0){
                // no udpgw port was set so don't start udp channel
                return;
            }
        }else {
           channelId =  String.format(Locale.US,"%s_%d_to_%s_%d_%d",
                    IPV4Header.getIPAddress(packet.getSourceAddress()),ByteUtil.getIntValue(packet.getSourcePort()),
                    IPV4Header.getIPAddress(packet.getDestAddress()),ByteUtil.getIntValue(packet.getDestPort())
                    ,packet.getProtocolNumber());
        }
        if (mChannels.get(channelId) != null){
            Objects.requireNonNull(mChannels.get(channelId)).onNewPacket(packet);
        }else {
            if (packet instanceof PacketV4){
                PacketV4 pk = (PacketV4) packet;
                Channel channel = null;
                if (pk.getTransmissionProtocol() instanceof TCP){
                    // do not create new channel if incoming packet is not TCP initial packet
                    if (((TCP) pk.getTransmissionProtocol()).getFlag().SYN == 0 ){
                        return;
                    }
                    channel = new SshChannelV4TCP(channelId, pk,mRemoteConnection,this);
                }else if (pk.getTransmissionProtocol() instanceof UDP && ByteUtil.getIntValue(pk.getDestPort()) == 53){
                    channel = new DNSChannel(channelId,pk,mRemoteConnection,this);
                } else if (pk.getTransmissionProtocol() instanceof UDP) {
                    channel = new UDPChannel(channelId,packet,mRemoteConnection,this,udpgwPort);
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
                ch.close();
            }
        }
    }
}
