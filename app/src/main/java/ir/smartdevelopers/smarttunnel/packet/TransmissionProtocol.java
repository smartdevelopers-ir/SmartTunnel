package ir.smartdevelopers.smarttunnel.packet;

import ir.smartdevelopers.smarttunnel.utils.ByteUtil;

public abstract class TransmissionProtocol {

    private byte[] mSourcePort;
    private byte[] mDestPort;

    public TransmissionProtocol(byte[] sourcePort, byte[] destPort) {
        mSourcePort = sourcePort;
        mDestPort = destPort;
    }
    public TransmissionProtocol(int sourcePort, int destPort) {
        mSourcePort = ByteUtil.getByteFromInt(sourcePort,2);
        mDestPort = ByteUtil.getByteFromInt(destPort,2);
    }
    public abstract void calculateChecksum(byte[] sourceIP,byte[] destIP,byte[] data);
    /**
     * Get Transition protocol header as byte array
     * before calling this methode, you must call {@link #calculateChecksum}
     * @throws IllegalStateException if checksum is null
     * */
    public abstract byte[] getHeader();
    public abstract byte getProtocolNumber();
    /**@return header bytes length*/
    public abstract int getHeaderLength();

    public byte[] getSourcePort() {
        return mSourcePort;
    }
    public int getSourcePortIntValue(){
        return ByteUtil.getIntValue(mSourcePort);
    }

    public void setSourcePort(byte[] sourcePort) {
        mSourcePort = sourcePort;
    }

    public byte[] getDestPort() {
        return mDestPort;
    }
    public int getDestPortIntValue(){
        return ByteUtil.getIntValue(mDestPort);
    }
    public void setDestPort(byte[] destPort) {
        mDestPort = destPort;
    }
}
