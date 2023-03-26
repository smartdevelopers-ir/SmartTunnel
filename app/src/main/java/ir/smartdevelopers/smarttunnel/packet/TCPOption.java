package ir.smartdevelopers.smarttunnel.packet;

public class TCPOption {

    private byte[] options;

    public TCPOption(byte[] options) {
        this.options = options;
    }

    public byte[] getBytes(){
        return options;
    }
    public static TCPOption fromByte(byte[] option){
        return new TCPOption(option);
    }
}
