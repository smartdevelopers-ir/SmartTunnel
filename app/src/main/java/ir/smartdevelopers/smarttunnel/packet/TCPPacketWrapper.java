package ir.smartdevelopers.smarttunnel.packet;

import java.util.Objects;

public class TCPPacketWrapper implements Comparable<TCPPacketWrapper>{
    private Packet mPacket;
    private int sequenceNumber;
    private int acknowledgeNumber;
    private int nextSequenceNumber;
    private boolean push;

    public TCPPacketWrapper(Packet packet) {
        mPacket = packet;
    }

    public Packet getPacket() {
        return mPacket;
    }

    public void setPacket(Packet packet) {
        mPacket = packet;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public int getAcknowledgeNumber() {
        return acknowledgeNumber;
    }

    public void setAcknowledgeNumber(int acknowledgeNumber) {
        this.acknowledgeNumber = acknowledgeNumber;
    }

    public int getNextSequenceNumber() {
        return nextSequenceNumber;
    }

    public void setNextSequenceNumber(int nextSequenceNumber) {
        this.nextSequenceNumber = nextSequenceNumber;
    }

    @Override
    public int compareTo(TCPPacketWrapper o) {
        return Long.compare(sequenceNumber,o.sequenceNumber);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TCPPacketWrapper that = (TCPPacketWrapper) o;
        return sequenceNumber == that.sequenceNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceNumber);
    }

    public boolean isPush() {
        return push;
    }

    public void setPush(boolean push) {
        this.push = push;
    }
}
