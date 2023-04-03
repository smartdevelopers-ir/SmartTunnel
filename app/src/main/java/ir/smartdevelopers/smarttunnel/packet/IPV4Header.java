package ir.smartdevelopers.smarttunnel.packet;

import java.nio.ByteBuffer;
import java.util.Arrays;

import ir.smartdevelopers.smarttunnel.utils.ArrayUtil;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;

public class IPV4Header extends IPHeader{
    public static byte IPV4 = 4;
    public static byte IPV6 = 6;


    /**
     * Type of Service:  8 bits
     * <p>
     * The Type of Service provides an indication of the abstract
     * parameters of the quality of service desired.  These parameters are
     * to be used to guide the selection of the actual service parameters
     * when transmitting a datagram through a particular network.  Several
     * networks offer service precedence, which somehow treats high
     * precedence traffic as more important than other traffic (generally
     * by accepting only traffic above a certain precedence at time of high
     * load).  The major choice is a three way tradeoff between low-delay,
     * high-reliability, and high-throughput.
     * <p>
     * Bits 0-2:  Precedence.
     * Bit    3:  0 = Normal Delay,      1 = Low Delay.
     * Bits   4:  0 = Normal Throughput, 1 = High Throughput.
     * Bits   5:  0 = Normal Relibility, 1 = High Relibility.
     * Bit  6-7:  Reserved for Future Use.
     * <p>
     * 0     1     2     3     4     5     6     7
     * +-----+-----+-----+-----+-----+-----+-----+-----+
     * |                 |     |     |     |     |     |
     * |   PRECEDENCE    |  D  |  T  |  R  |  0  |  0  |
     * |                 |     |     |     |     |     |
     * +-----+-----+-----+-----+-----+-----+-----+-----+
     * <p>
     * Precedence
     * <p>
     * 111 - Network Control
     * 110 - Internetwork Control
     * 101 - CRITIC/ECP
     * 100 - Flash Override
     * 011 - Flash
     * 010 - Immediate
     * 001 - Priority
     * 000 - Routine
     */
    private byte mTypeOfService;
    /**
     * Total Length:  16 bits
     * <p>
     * Total Length is the length of the datagram, measured in octets,
     * including internet header and data.  This field allows the length of
     * a datagram to be up to 65,535 octets.  Such long datagrams are
     * impractical for most hosts and networks.  All hosts must be prepared
     * to accept datagrams of up to 576 octets (whether they arrive whole
     * or in fragments).  It is recommended that hosts only send datagrams
     * larger than 576 octets if they have assurance that the destination
     * is prepared to accept the larger datagrams.
     * <p>
     * The number 576 is selected to allow a reasonable sized data block to
     * be transmitted in addition to the required header information.  For
     * example, this size allows a data block of 512 octets plus 64 header
     * octets to fit in a datagram.  The maximal internet header is 60
     * octets, and a typical internet header is 20 octets, allowing a
     * margin for headers of higher level protocols.
     */
    private byte[] mTotalLength;
    /**
     * Identification:  16 bits
     * <p>
     * An identifying value assigned by the sender to aid in assembling the
     * fragments of a datagram.
     */
    private byte[] mIdentification;
    /**
     * Flags:  3 bits
     * <p>
     * Various Control Flags.
     * <p>
     * Bit 0: reserved, must be zero
     * Bit 1: (DF) 0 = May Fragment,  1 = Don't Fragment.
     * Bit 2: (MF) 0 = Last Fragment, 1 = More Fragments.
     * <p>
     * 0   1   2
     * +---+---+---+
     * |   | D | M |
     * | 0 | F | F |
     * +---+---+---+
     */
    private byte mFlag;
    /**
     * Fragment Offset:  13 bits
     * <p>
     * This field indicates where in the datagram this fragment belongs.
     * The fragment offset is measured in units of 8 octets (64 bits).  The
     * first fragment has offset zero.
     */
    private short mFragmentOffset;
    /**
     * Time to Live:  8 bits
     * <p>
     * This field indicates the maximum time the datagram is allowed to
     * remain in the internet system.  If this field contains the value
     * zero, then the datagram must be destroyed.  This field is modified
     * in internet header processing.  The time is measured in units of
     * seconds, but since every module that processes a datagram must
     * decrease the TTL by at least one even if it process the datagram in
     * less than a second, the TTL must be thought of only as an upper
     * bound on the time a datagram may exist.  The intention is to cause
     * undeliverable datagrams to be discarded, and to bound the maximum
     * datagram lifetime.
     */
    private byte mTimeToLive;
    /**
     * Protocol:  8 bits
     * <p>
     * This field indicates the next level protocol used in the data
     * portion of the internet datagram.  The values for various protocols
     * are specified in "Assigned Numbers"
     */
    private byte mProtocol;
    /**
     * Header Checksum:  16 bits
     * <p>
     * A checksum on the header only.  Since some header fields change
     * (e.g., time to live), this is recomputed and verified at each point
     * that the internet header is processed.
     * <p>
     * The checksum algorithm is:
     * <p>
     * The checksum field is the 16 bit one's complement of the one's
     * complement sum of all 16 bit words in the header.  For purposes of
     * computing the checksum, the value of the checksum field is zero.
     * <p>
     * This is a simple to compute checksum and experimental evidence
     * indicates it is adequate, but it is provisional and may be replaced
     * by a CRC procedure, depending on further experience.
     */
    private byte[] mHeaderChecksum;

    private byte[] mOptions;

    /**
     * IP header holder
     * */
    private byte[] mHeader;

    public IPV4Header(byte[] sourceAddress, byte[] destAddress) {
        super(sourceAddress, destAddress);
    }



    public void setTypeOfService(byte typeOfService) {
        mTypeOfService = typeOfService;
    }

    public void setTotalLength(byte[] totalLength) {
        mTotalLength = totalLength;
    }

    public void setIdentification(byte[] identification) {
        mIdentification = identification;
    }

    public void setFlag(byte flag) {
        mFlag = flag;
    }

    public void setFragmentOffset(short fragmentOffset) {
        mFragmentOffset = fragmentOffset;
    }

    public void setTimeToLive(byte timeToLive) {
        mTimeToLive = timeToLive;
    }

    /**
     * Version:  4 bits
     * <p>
     * The Version field indicates the format of the internet header.
     */
    public byte getVersion() {
        return 4;
    }

    public byte[] getTotalLength() {
        return mTotalLength;
    }

    public byte getFlag() {
        return mFlag;
    }

    public short getFragmentOffset() {
        return mFragmentOffset;
    }

    public byte getTimeToLive() {
        return mTimeToLive;
    }

    public byte[] getOptions() {
        return mOptions;
    }

    public byte getProtocol() {
        return mProtocol;
    }

    /**
     * IHL:  4 bits
     * <p>
     * Internet Header Length is the length of the internet header in 32
     * bit words, and thus points to the beginning of the data.  Note that
     * the minimum value for a correct header is 5.
     * @return header's bytes length. It just convert 32 bit word from length to byte
     * so when you need to get IHL you must divide returned value by 4
     */
    public int getHeaderLength() {
        return 5 * 4 + (mOptions == null ? 0 : mOptions.length);
    }

    public void setProtocol(byte protocol) {
        mProtocol = protocol;
    }

    public void setHeaderChecksum(byte[] headerChecksum) {
        mHeaderChecksum = headerChecksum;
    }

    public void setDestAddress(byte[] destAddress) {
        super.setDestAddress(destAddress);
    }

    public void setOptions(byte[] options) {
        mOptions = options;
    }
    /**
     * source IP address. if IP address is 192.168.1.1 so it must be [-58,-88,1,1]
     * all ip sub number must convert to its byte value
     */
    @Override
    public byte[] getSourceAddress() {
        return super.getSourceAddress();
    }

    public String getSourceAddressName() {
        return getIPAddress(getSourceAddress());
    }
    /**
     * destination IP address. if IP address is 192.168.1.1 so it must be [-58,-88,1,1]
     * all ip sub number must convert to its byte value
     */
    public byte[] getDestAddress() {
        return super.getDestAddress();
    }
    public String getDestAddressName() {
        return getIPAddress(getDestAddress());
    }

    public byte[] getIdentification() {
        return mIdentification;
    }

    public byte getTypeOfService() {
        return mTypeOfService;
    }

    public void calculateChecksum(){

        mHeader = generateInitialHeader();
        mHeaderChecksum = ByteUtil.computeChecksum(mHeader);
    }

    public byte[] getHeader(){
        if (mHeaderChecksum == null){
            calculateChecksum();
        }
        if (mHeader == null){
            mHeader = generateInitialHeader();
        }
        ArrayUtil.replace(mHeader,10,mHeaderChecksum);
        return mHeader;
    }
    public byte[] generateInitialHeader(){
        ByteBuffer buffer=ByteBuffer.allocate(getHeaderLength());
        buffer.put((byte) ((getVersion() << 4) | (getHeaderLength() / 4)));
        buffer.put(mTypeOfService);
        buffer.put(mTotalLength);
        buffer.put(mIdentification);
        int flagAndFragmentOffset = (mFlag << 13) | (mFragmentOffset & 0x1FFF);
        buffer.put(ByteUtil.getByteFromInt(flagAndFragmentOffset,2));
        buffer.put(mTimeToLive);
        buffer.put(mProtocol);
        buffer.put(new byte[]{0,0});// checksum
        buffer.put(getSourceAddress());
        buffer.put(getDestAddress());
        if (mOptions != null ){
            buffer.put(mOptions);
        }
        return buffer.array();

    }
    public static String getIPAddress(byte[] address){
        StringBuilder builder=new StringBuilder();
        for (int i=0;i<address.length;i++){
            builder.append(((int)address[i]) & 0xFF);
            if (i < address.length-1){
                builder.append(".");
            }
        }
        return builder.toString();
    }
    public static IPV4Header fromHeaderByte(byte[] header){
        byte[] sourceAddress=Arrays.copyOfRange(header,12,16);
        byte[] destAddress=Arrays.copyOfRange(header,16,20);
        IPV4Header IPV4Header =new IPV4Header(sourceAddress,destAddress);
        int ipHeaderBytesLength = (header[0] & 0xF) * 4;
        IPV4Header.mTypeOfService = header[1];
        IPV4Header.mTotalLength = Arrays.copyOfRange(header,2,4);
        IPV4Header.mIdentification = Arrays.copyOfRange(header,4,6);
        IPV4Header.mFlag = (byte) (header[6] >> 5);
        IPV4Header.mFragmentOffset = (short) (ByteUtil.getIntValue(Arrays.copyOfRange(header,6,8)) & 0x1FFF);
        IPV4Header.mTimeToLive = header[8];
        IPV4Header.mProtocol = header[9];
        IPV4Header.mHeaderChecksum = Arrays.copyOfRange(header,10,12);
        if (ipHeaderBytesLength > 20 ){
            IPV4Header.mOptions = Arrays.copyOfRange(header,20,ipHeaderBytesLength);
        }
        return IPV4Header;
    }
}
