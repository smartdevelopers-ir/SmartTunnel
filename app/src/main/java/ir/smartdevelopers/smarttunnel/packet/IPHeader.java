package ir.smartdevelopers.smarttunnel.packet;

public abstract class IPHeader {
    private byte[] mSourceAddress;
    private byte[] mDestAddress;

    public IPHeader(byte[] sourceAddress, byte[] destAddress) {
        mSourceAddress = sourceAddress;
        mDestAddress = destAddress;
    }

    /**@return All header as byte array*/
    public abstract byte[] getHeader();
    /**@return header bytes length*/
    public abstract int getHeaderLength();
    /**@return ip version*/
    public abstract byte getVersion();

    public byte[] getSourceAddress() {
        return mSourceAddress;
    }

    public void setSourceAddress(byte[] sourceAddress) {
        mSourceAddress = sourceAddress;
    }

    public byte[] getDestAddress() {
        return mDestAddress;
    }

    public void setDestAddress(byte[] destAddress) {
        mDestAddress = destAddress;
    }
}
