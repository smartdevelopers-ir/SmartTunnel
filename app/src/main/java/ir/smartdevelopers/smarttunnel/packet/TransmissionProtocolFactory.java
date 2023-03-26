package ir.smartdevelopers.smarttunnel.packet;

public interface TransmissionProtocolFactory {
    TransmissionProtocol of(byte protocol,byte[] transmissionLayerData);
}
