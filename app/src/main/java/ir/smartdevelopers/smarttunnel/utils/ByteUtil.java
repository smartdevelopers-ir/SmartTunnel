package ir.smartdevelopers.smarttunnel.utils;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.Arrays;

public class ByteUtil {
    public static byte[] getByteFromInt(int value,int minByteArraySize){
        int leadingZero = Integer.numberOfLeadingZeros(value);
        int byteCount = 4 - leadingZero/8;
        byteCount = Integer.max(byteCount,minByteArraySize);
        byte[] r = new byte[byteCount];
        for (int i = byteCount-1 ; i >= 0 ; i--){
            byte a = (byte) (value & 0xFF);
            r[i] = a;
            value >>>= 8;
        }
        return r;
    }
    public static int getIntValue(byte[] value){
        if (value.length >4){
            throw new NumberFormatException("Integer is 4 byte, can not convert more than 4 byte to int");
        }
        int r=0;
        int shift=(value.length -1 )* 8;
        for (byte b : value) {
            r |= (b & 0xFF ) << shift;
            shift -=8;
        }
        return r;
    }
    public static byte[] computeChecksum(byte[] data){
        int sum = onesComplementSum16bit(data);
        return getByteFromInt((~sum & 0xFFFF),2);
    }
    public static int onesComplementSum16bit(byte[] data){
        if (data.length % 2 !=0){
            data= Arrays.copyOf(data,data.length+1);
        }
        int sum=0;

        for (int i = 0 ; i<data.length;i+=2){
            int b2 = ((data[i] & 0xFF ) << 8)  | (data[i+1] & 0xFF);
            int r = (sum + b2);
            if (r > 0xFFFF){
                r = r & 0xFFFF;
                r+=1;
            }
            sum = r;

        }
        return sum;
    }

    public static String hash(byte[] value){
        return DigestUtils.sha1Hex(value);
    }

    public static void clear(byte[] data){
        Arrays.fill(data, (byte) 0);
    }
}
