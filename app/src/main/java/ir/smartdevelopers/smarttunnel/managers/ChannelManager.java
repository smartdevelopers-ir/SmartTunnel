package ir.smartdevelopers.smarttunnel.managers;

import ir.smartdevelopers.smarttunnel.channels.Channel;
import ir.smartdevelopers.smarttunnel.packet.Packet;

public abstract class ChannelManager {
    protected PacketManager mPacketManager;
    public abstract void sendToRemoteServer(Packet packet);
    public abstract void destroy();
    public abstract void removeChannel(Channel channel);
    public abstract void sendToLocal(Packet packet);
    public void setPacketManager(PacketManager packetManager) {
        mPacketManager = packetManager;
    }
}
