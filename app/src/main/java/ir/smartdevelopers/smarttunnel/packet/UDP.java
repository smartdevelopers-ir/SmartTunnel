package ir.smartdevelopers.smarttunnel.packet;

import java.nio.ByteBuffer;
import java.util.Arrays;

import ir.smartdevelopers.smarttunnel.utils.ArrayUtil;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;

public class UDP extends TransmissionProtocol{
    public static final byte PROTOCOL_NUMBER = 17;

    private byte[] mChecksum;
    private byte[] mDatagramLength;
    private byte[] mHeader;

    public UDP(byte[] sourcePort, byte[] destPort) {
        super(sourcePort, destPort);
    }

    public UDP(int sourcePort, int destPort) {
        super(sourcePort, destPort);
    }

    @Override
    public void calculateChecksum(byte[] sourceIP, byte[] destIP, byte[] data) {
        int dataLength = (data == null ? 0 : data.length);
        mDatagramLength = ByteUtil.getByteFromInt((getHeaderLength() + dataLength) ,2);
        ByteBuffer pseudoHeader = ByteBuffer.allocate(12 + 8 + dataLength );
        pseudoHeader.put(sourceIP);
        pseudoHeader.put(destIP);
        pseudoHeader.put(new byte[]{0,getProtocolNumber()});
        pseudoHeader.put(mDatagramLength);
        mHeader = generateInitialHeader(data);
        pseudoHeader.put(mHeader);
        if (data != null){
            pseudoHeader.put(data);
        }
        mChecksum = ByteUtil.computeChecksum(pseudoHeader.array());

    }
    private byte[] generateInitialHeader(byte[] data){
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.put(getSourcePort());
        buffer.put(getDestPort());
        buffer.put(ByteUtil.getByteFromInt(8 + (data != null ? data.length : 0),2));
        buffer.put(new byte[] {0,0}); // checksum must be zero for initial header
        return buffer.array();
    }

    @Override
    public byte[] getHeader() {
        if (mHeader == null || mChecksum == null){
            throw new IllegalStateException("You must call calculateChecksum first");
        }
        ArrayUtil.replace(mHeader,6,mChecksum);
        return mHeader;
    }

    @Override
    public byte getProtocolNumber() {
        return PROTOCOL_NUMBER;
    }

    @Override
    public int getHeaderLength() {
        return 8;
    }

    public static UDP fromHeaderBytes(byte[] header){
        byte[] sPort = Arrays.copyOfRange(header,0,2);
        byte[] dPort = Arrays.copyOfRange(header,2,4);
        UDP udp = new UDP(sPort,dPort);
        udp.mDatagramLength = Arrays.copyOfRange(header,4,6);
        udp.mChecksum = Arrays.copyOfRange(header,6,8);
        udp.mHeader = Arrays.copyOfRange(header,0,8);
        return udp;
    }
}
