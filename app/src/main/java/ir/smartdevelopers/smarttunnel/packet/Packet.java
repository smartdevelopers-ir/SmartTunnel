package ir.smartdevelopers.smarttunnel.packet;

public abstract class Packet {


    public static int MAX_SIZE = 32*1024;
    public abstract byte[] getPacketBytes();
    public abstract byte[] getSourceAddress();
    public abstract byte[] getDestAddress();
    public abstract byte[] getSourcePort();
    public abstract byte[] getDestPort();
    public abstract byte getProtocolNumber();
    public abstract TransmissionProtocol getTransmissionProtocol() ;
    public abstract byte[] getData();
    public interface PacketCreator{
        Packet create(TransmissionProtocol transmissionProtocol,byte[] data);
    }

}
