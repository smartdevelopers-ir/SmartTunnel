package ir.smartdevelopers.smarttunnel.packet;

public interface IPHeaderFactory {
    IPV4Header of(byte[] internetData);
}
