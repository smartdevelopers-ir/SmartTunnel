package ir.smartdevelopers.smarttunnel.packet;

public class TCPFlag {
    public byte CWR;
    public byte ECE;
    public byte URG;
    public byte ACK;
    public byte PSH;
    public byte RST;
    public byte SYN;
    public byte FIN;

    public static TCPFlag fromByte(byte flags) {
        TCPFlag flag = new TCPFlag();
        flag.CWR = (byte) ((flags & 0b10000000) >> 7);
        flag.ECE = (byte) ((flags & 0b01000000) >> 6);
        flag.URG = (byte) ((flags & 0b00100000) >> 5);
        flag.ACK = (byte) ((flags & 0b00010000) >> 4);
        flag.PSH = (byte) ((flags & 0b00001000) >> 3);
        flag.RST = (byte) ((flags & 0b00000100) >> 2);
        flag.SYN = (byte) ((flags & 0b00000010) >> 1);
        flag.FIN = (byte) (flags & 0b00000001);
        return flag;
    }
    public static TCPFlag SYNOnly(){
        TCPFlag flag = new TCPFlag();
        flag.SYN=1;
        return flag;
    }
    public static TCPFlag ACKOnly(){
        TCPFlag flag = new TCPFlag();
        flag.ACK=1;
        return flag;
    }

    public static TCPFlag SYN_ACK(){
        TCPFlag flag = new TCPFlag();
        flag.SYN=1;
        flag.ACK=1;
        return flag;
    }
    public static TCPFlag PSH_ACK(){
        TCPFlag flag = new TCPFlag();
        flag.PSH=1;
        flag.ACK=1;
        return flag;
    }
    public static TCPFlag RST_ACK(){
        TCPFlag flag = new TCPFlag();
        flag.RST=1;
        flag.ACK=1;
        return flag;
    }
    public static TCPFlag FIN_ACK(){
        TCPFlag flag = new TCPFlag();
        flag.FIN=1;
        flag.ACK=1;
        return flag;
    }
    public byte getByte(){
        return  (byte) ((CWR << 7) | (ECE << 6) | (URG << 5) |
                        (ACK << 4) | (PSH << 3) | (RST << 2) |
                        (SYN << 1) | FIN);
    }
}
