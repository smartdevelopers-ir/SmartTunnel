package ir.smartdevelopers.smarttunnel.packet;

import java.nio.ByteBuffer;
import java.util.Arrays;

import ir.smartdevelopers.smarttunnel.utils.ArrayUtil;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;

public class TCP extends TransmissionProtocol {
    public static byte PROTOCOL_NUMBER = 6;
    /**
     * Sequence number (32 bits)
     * Has a dual role:
     * <ul>
     * <li>
     * If the SYN flag is set (1), then this is the initial sequence number. The sequence number
     * of the actual first data byte and the acknowledged number in the corresponding ACK are then
     * this sequence number plus 1.
     * </lil>
     * <li>
     * If the SYN flag is clear (0), then this is the accumulated sequence number of the first
     * data byte of this segment for the current session.
     * </li>
     * </ul>
     */
    private byte[] mSequenceNumber;
    /**
     * Acknowledgment number (32 bits)
     * If the ACK flag is set then the value of this field is the next sequence number that the
     * sender of the ACK is expecting.This acknowledges receipt of all prior bytes (if any).
     * The first ACK sent by each end acknowledges the other end's initial sequence number itself,
     * but no data.
     */
    private byte[] mAcknowledgmentNumber;
    /**
     * Data offset (4 bits)
     * Specifies the size of the TCP header in 32-bit words. The minimum size header is 5 words and
     * the maximum is 15 words thus giving the minimum size of 20 bytes and maximum of 60 bytes,
     * allowing for up to 40 bytes of options in the header. This field gets its name from the fact
     * that it is also the offset from the start of the TCP segment to the actual data.
     */
    private byte dataOffset;
    /**
     * Flags (8 bits)
     * Contains 8 1-bit flags (control bits) as follows:
     * <li>CWR (1 bit): Congestion window reduced (CWR) flag is set by the sending host to indicate
     * that it received a TCP segment with the ECE flag set and had responded in congestion control
     * mechanism.</li>
     * <li>ECE (1 bit): ECN-Echo has a dual role, depending on the value of the SYN flag. It indicates:
     * <ul><li>If the SYN flag is set (1), that the TCP peer is ECN capable.</li>
     * <li>If the SYN flag is clear (0), that a packet with Congestion Experienced flag set
     * (ECN=11) in the IP header was received during normal transmission. This serves as an
     * indication of network congestion (or impending congestion) to the TCP sender</li></ul>.</li>
     * <li>URG (1 bit): Indicates that the Urgent pointer field is significant</li>
     * <li>ACK (1 bit): Indicates that the Acknowledgment field is significant.
     * All packets after the initial SYN packet sent by the client should have this flag set.</li>
     * <li>PSH (1 bit): Push function. Asks to push the buffered data to the receiving application.</li>
     * <li>RST (1 bit): Reset the connection</li>
     * <li>SYN (1 bit): Synchronize sequence numbers. Only the first packet sent from each end should
     * have this flag set. Some other flags and fields change meaning based on this flag,
     * and some are only valid when it is set, and others when it is clear.</li>
     * <li>FIN (1 bit): Last packet from sender</li>
     * */
    private TCPFlag mFlag;
    /**
     * Checksum (16 bits)
     * The 16-bit checksum field is used for error-checking of the TCP header, the payload and an
     * IP pseudo-header. The pseudo-header consists of the source IP address,
     * the destination IP address, the protocol number for the TCP protocol and
     * the length of the TCP headers and payload (in bytes).
     * */
    private byte[] mChecksum;
    /**
     * Window size (16 bits)
     * The size of the receive window, which specifies the number of window size units
     * that the sender of this segment is currently willing to receive
     * */
    private byte[] mWindowSize;
    /**
     * Urgent pointer (16 bits)
     * If the URG flag is set, then this 16-bit field is an offset from the sequence number
     * indicating the last urgent data byte.
     * */
    private byte[] mUrgentPointer;

    /**
     * Options (Variable 0–320 bits, in units of 32 bits)
     * The length of this field is determined by the data offset field. Options have up to
     * three fields: Option-Kind (1 byte), Option-Length (1 byte), Option-Data (variable).
     * The Option-Kind field indicates the type of option and is the only field that is not optional.
     * Depending on Option-Kind value, the next two fields may be set. Option-Length indicates
     * the total length of the option, and Option-Data contains data associated with the option,
     * if applicable. For example, an Option-Kind byte of 1 indicates that this is a no operation
     * option used only for padding, and does not have an Option-Length or Option-Data fields
     * following it. An Option-Kind byte of 0 marks the end of options, and is also only one byte.
     * An Option-Kind byte of 2 is used to indicate Maximum Segment Size option,
     * and will be followed by an Option-Length byte specifying the length of the MSS field.
     * Option-Length is the total length of the given options field, including Option-Kind
     * and Option-Length fields. So while the MSS value is typically expressed in two bytes,
     * Option-Length will be 4. As an example, an MSS option field with
     * a value of 0x05B4 is coded as (0x02 0x04 0x05B4) in the TCP options section.
     * Some options may only be sent when SYN is set; they are indicated below as [SYN].
     * Option-Kind and standard lengths given as (Option-Kind, Option-Length).
     * */
    private TCPOption mTCPOption;
    /**This is TCP header holder*/
    private byte[] mHeader;

    public TCP(byte[] sourcePort, byte[] destPort) {
        super(sourcePort, destPort);
    }

    public TCP(int sourcePort, int destPort) {
        super(sourcePort, destPort);
    }

    /**
     * For calculating TCP checksum we must combine pseudo-header,TCP-header and data,
     * Then calculate 16 bit One's complement of One's complement of sum of these combined
     * 16 bit words, checksum itself must be zero in TCP-header for calculation
     * The pseudo-header consists of the source IP address,
     * the destination IP address, the protocol number for the TCP protocol and
     * the length of the TCP headers and payload (in bytes).
     */
    @Override
    public void calculateChecksum(byte[] sourceIP, byte[] destIP, byte[] data) {
        int dataLength = data == null ? 0 : data.length;
        ByteBuffer pseudoHeader = ByteBuffer.allocate(12+getHeaderLength()+dataLength);
        pseudoHeader.put(sourceIP);
        pseudoHeader.put(destIP);
        pseudoHeader.put(new byte[]{0,getProtocolNumber()});
        pseudoHeader.put(ByteUtil.getByteFromInt(getHeaderLength()+dataLength,2));
        mHeader = generateInitialHeader();
        pseudoHeader.put(mHeader);
        if (data != null){
            pseudoHeader.put(data);
        }
        mChecksum = ByteUtil.computeChecksum(pseudoHeader.array());

    }

    @Override
    public byte[] getHeader() {
        if (mChecksum == null){
            throw new IllegalStateException("You must call calculateChecksum first");
        }
        if (mHeader == null){
            mHeader = generateInitialHeader();
        }
        ArrayUtil.replace(mHeader,16,mChecksum);
        return mHeader;
    }
    private byte[] generateInitialHeader(){
        ByteBuffer buffer=ByteBuffer.allocate(5*4 + (mTCPOption == null ? 0 : mTCPOption.getBytes().length));
        buffer.put(getSourcePort());
        buffer.put(getDestPort());
        buffer.put(mSequenceNumber);
        buffer.put(mAcknowledgmentNumber);
        byte dataOffset = (byte) (5 + (mTCPOption == null ? 0 : mTCPOption.getBytes().length/4));
        int dataOffsetAndFlags= (dataOffset << 12) | (mFlag == null ? 0 : mFlag.getByte());
        buffer.put(ByteUtil.getByteFromInt(dataOffsetAndFlags,2));
        buffer.put(mWindowSize);
        buffer.put(new byte[]{0,0});// checksum
        buffer.put(mUrgentPointer);
        if (mTCPOption!=null){
            buffer.put(mTCPOption.getBytes());
        }

        return buffer.array();
    }

    @Override
    public byte getProtocolNumber() {
        return 6;
    }

    @Override
    public int getHeaderLength() {
        return 5 * 4 + (mTCPOption == null ? 0 : mTCPOption.getBytes().length);
    }

    public void setSequenceNumber(int sequenceNumber) {
        mSequenceNumber = ByteUtil.getByteFromInt(sequenceNumber,4);
    }
    public void setSequenceNumber(byte[] sequenceNumber) {
        mSequenceNumber = sequenceNumber;
    }
    public void setAcknowledgmentNumber(int acknowledgmentNumber) {
        mAcknowledgmentNumber = ByteUtil.getByteFromInt(acknowledgmentNumber,4);
    }
    public void setAcknowledgmentNumber(byte[] acknowledgmentNumber) {
        mAcknowledgmentNumber = acknowledgmentNumber;
    }
    public void setFlag(TCPFlag flag) {
        mFlag = flag;
    }

    public void setWindowSize(int windowSize) {
        mWindowSize = ByteUtil.getByteFromInt(windowSize,2);
    }
    public void setWindowSize(byte[] windowSize) {
        mWindowSize = windowSize;
    }
    public void setUrgentPointer(int urgentPointer) {
        mUrgentPointer = ByteUtil.getByteFromInt(urgentPointer,2);
    }
    public void setUrgentPointer(byte[] urgentPointer) {
        mUrgentPointer = urgentPointer;
    }
    public void setTCPOption(TCPOption TCPOption) {
        mTCPOption = TCPOption;
    }

    public int getSequenceNumberIntValue() {
        return ByteUtil.getIntValue(mSequenceNumber);
    }

    public byte[] getSequenceNumber() {
        return mSequenceNumber;
    }

    public int getAcknowledgmentNumberIntValue() {
        return ByteUtil.getIntValue(mAcknowledgmentNumber);
    }

    public byte[] getAcknowledgmentNumber() {
        return mAcknowledgmentNumber;
    }

    public byte getDataOffset() {
        return dataOffset;
    }

    public TCPFlag getFlag() {
        return mFlag;
    }

    public byte[] getChecksum() {
        return mChecksum;
    }
    public static TCP fromTCPHeader(byte[] header){

        byte[] sPort = Arrays.copyOfRange(header,0,2);
        byte[] dPort = Arrays.copyOfRange(header,2,4);
        TCP tcp=new TCP(sPort,dPort);
        tcp.mSequenceNumber = Arrays.copyOfRange(header,4,8);
        tcp.mAcknowledgmentNumber = Arrays.copyOfRange(header,8,12);
        tcp.dataOffset = (byte) ((header[12] & 0xFF) >> 4);
        tcp.mFlag = TCPFlag.fromByte(header[13]);
        tcp.mWindowSize = Arrays.copyOfRange(header,14,16);
        tcp.mChecksum = Arrays.copyOfRange(header,16,18);
        tcp.mUrgentPointer = Arrays.copyOfRange(header,18,20);
        if (header.length >20 && tcp.dataOffset > 5){
            tcp.mTCPOption = TCPOption.fromByte(Arrays.copyOfRange(header,20,tcp.dataOffset * 4));
        }
        return tcp;
    }

    public void resetChecksum() {
        mChecksum = new byte[]{0,0};
    }
}
