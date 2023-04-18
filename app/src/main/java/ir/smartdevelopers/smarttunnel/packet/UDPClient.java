package ir.smartdevelopers.smarttunnel.packet;

import java.util.Arrays;
import java.util.Objects;

public class UDPClient {
    /** This id is for ip header identification*/
    public short id;
    public int localPort;
    public byte[] localAddress;
    public int remotePort;
    public byte[] remoteAddress;
    public UDPClient(int localPort, byte[] localAddress,int remotePort,byte[] remoteAddress) {
        this.localPort = localPort;
        this.localAddress = localAddress;
        this.remotePort = remotePort;
        this.remoteAddress = remoteAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UDPClient client = (UDPClient) o;
        return localPort == client.localPort && remotePort == client.remotePort && Arrays.equals(localAddress, client.localAddress) && Arrays.equals(remoteAddress, client.remoteAddress);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(localPort, remotePort);
        result = 31 * result + Arrays.hashCode(localAddress);
        result = 31 * result + Arrays.hashCode(remoteAddress);
        return result;
    }
}
