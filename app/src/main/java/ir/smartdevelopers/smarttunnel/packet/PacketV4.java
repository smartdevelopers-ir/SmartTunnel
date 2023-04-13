package ir.smartdevelopers.smarttunnel.packet;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;

import ir.smartdevelopers.smarttunnel.utils.ByteUtil;

public class PacketV4 extends Packet{

    private final IPV4Header mIPV4Header;
    protected TransmissionProtocol mTransmissionProtocol;
    protected byte[] data;
    public PacketV4(byte[] packet, IPV4Header ipv4Header, TransmissionProtocolFactory protocolFactory){
        if (packet.length > MAX_SIZE){
            throw new IllegalArgumentException("packet size can not bet mor than "+MAX_SIZE+" bytes");
        }

        mIPV4Header = ipv4Header;
        mTransmissionProtocol = protocolFactory.of(mIPV4Header.getProtocol(),
                Arrays.copyOfRange(packet, mIPV4Header.getHeaderLength(),packet.length));
        int dataOffset = mTransmissionProtocol.getHeaderLength();

        if (packet.length > mIPV4Header.getHeaderLength() + dataOffset){
            data = Arrays.copyOfRange(packet, mIPV4Header.getHeaderLength()+dataOffset, ByteUtil.getIntValue(mIPV4Header.getTotalLength()));
        }

    }
    public PacketV4(IPV4Header IPV4Header, TransmissionProtocol transmissionProtocol, byte[] data){
        if (IPV4Header.getHeaderLength() + transmissionProtocol.getHeaderLength() +
                (data == null ? 0 : data.length) > MAX_SIZE){
            throw new IllegalArgumentException("packet size can not bet mor than "+MAX_SIZE+" bytes");
        }
        mIPV4Header = IPV4Header;
        mTransmissionProtocol = transmissionProtocol;
        this.data = data;
    }
    @Override
    public byte[] getPacketBytes(){
        int totalLength = mIPV4Header.getHeaderLength() + mTransmissionProtocol.getHeaderLength() + (data == null ? 0 : data.length);
        mIPV4Header.setTotalLength(ByteUtil.getByteFromInt(totalLength,2));
        mIPV4Header.setHeaderChecksum(null);
        ByteBuffer buffer=ByteBuffer.allocate(mIPV4Header.getHeaderLength()+mTransmissionProtocol.getHeaderLength() +
                (data == null ? 0 : data.length));
        buffer.put(mIPV4Header.getHeader());
        mTransmissionProtocol.calculateChecksum(mIPV4Header.getSourceAddress(), mIPV4Header.getDestAddress(),data);
        buffer.put(mTransmissionProtocol.getHeader());
        if (data != null){
            buffer.put(data);
        }
        return buffer.array();
    }

    @Override
    public byte[] getSourceAddress() {
        return mIPV4Header.getSourceAddress();
    }

    @Override
    public byte[] getDestAddress() {
        return mIPV4Header.getDestAddress();
    }

    @Override
    public byte[] getSourcePort() {
        return mTransmissionProtocol.getSourcePort();
    }

    @Override
    public byte[] getDestPort() {
        return mTransmissionProtocol.getDestPort();
    }

    @Override
    public byte getProtocolNumber() {
        return getIPHeader().getProtocol();
    }

    public IPV4Header getIPHeader() {
        return mIPV4Header;
    }

    public TransmissionProtocol getTransmissionProtocol() {
        return mTransmissionProtocol;
    }

    public byte[] getData() {
        return data;
    }

    @NonNull
    @Override
    public String toString() {
       if (mTransmissionProtocol instanceof TCP){
           TCP tcp = (TCP) mTransmissionProtocol;
           return String.format(Locale.ENGLISH,
                   "%s:%d to %s:%d flag :%s - seq = %d - ack = %d - dataLength = %d",
                   getIPHeader().getSourceAddressName(),tcp.getSourcePortIntValue(),
                   getIPHeader().getDestAddressName(),tcp.getDestPortIntValue(),
                   Integer.toBinaryString(tcp.getFlag().getByte()),
                   tcp.getSequenceNumberIntValue(),tcp.getAcknowledgmentNumberIntValue(),
                   getData() == null ? 0 : getData().length);
       }
       return super.toString();
    }
}
