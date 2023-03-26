package ir.smartdevelopers.smarttunnel.utils;

public class ArrayUtil {
    public static void replace(byte[] source,int startIndex,byte[] data){
        if (source.length < startIndex + data.length){
            throw new ArrayIndexOutOfBoundsException("source array length is less than data length from index");
        }
        int n=0;
        for (int i = startIndex;i<startIndex+data.length;i++){
            source[i]=data[n++];
        }
    }
}
