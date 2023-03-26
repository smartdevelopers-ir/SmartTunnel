package ir.smartdevelopers.smarttunnel.channels;

import java.io.Closeable;
import java.util.Arrays;

import ir.smartdevelopers.smarttunnel.packet.Packet;

public abstract class Channel  implements Runnable{
    private final byte[] mLocalPort;
    private final byte[] mRemotePort;
    private final byte[] mLocalAddress;
    private final byte[] mRemoteAddress;

    public Channel(byte[] localPort, byte[] remotePort, byte[] localAddress, byte[] remoteAddress) {
        mLocalPort = localPort;
        mRemotePort = remotePort;
        mLocalAddress = localAddress;
        mRemoteAddress = remoteAddress;
    }
    public abstract void onNewPacket(Packet packet);
    public byte[] getLocalPort() {
        return mLocalPort;
    }

    public byte[] getRemotePort() {
        return mRemotePort;
    }

    public byte[] getLocalAddress() {
        return mLocalAddress;
    }

    public byte[] getRemoteAddress() {
        return mRemoteAddress;
    }


    public abstract void close();
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Channel channel = (Channel) o;
        return Arrays.equals(mLocalPort, channel.mLocalPort) && Arrays.equals(mLocalAddress, channel.mLocalAddress);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(mLocalPort);
        result = 31 * result + Arrays.hashCode(mLocalAddress);
        return result;
    }
}
