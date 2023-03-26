package ir.smartdevelopers.smarttunnel;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.BinaryCodec;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Test;

import static org.junit.Assert.*;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;


import de.taimos.totp.TOTP;
import ir.smartdevelopers.smarttunnel.packet.IPV4Header;
import ir.smartdevelopers.smarttunnel.packet.TCPFlag;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() throws NoSuchAlgorithmException {
        String sectet="Q4XOABWLBA4YROCV";
        Base32 base32 = new Base32();
        byte[] secBytes= base32.decode(sectet);
        String hexKey = Hex.encodeHexString(secBytes);
        System.out.println("hexKey = "+hexKey);
        String totp=TOTP.getOTP(hexKey);
        System.out.println("totp = "+totp);
        long step = System.currentTimeMillis() / 30000;
        String steps = Long.toHexString(17).toUpperCase();
        System.out.println("steps ="+steps);
        BigInteger bi=new BigInteger("12345678901234567890123456789");
        BigInteger bip=BigInteger.probablePrime(300, new Random());
        System.out.println("prime = "+bip);
    }
    @Test
    public void byteCoptyTest(){
        byte[] a={1,2,3,4,5,6};
        byte[] b = Arrays.copyOfRange(a,0,2*2);
        byte[] c= Arrays.copyOfRange(a,2*2,a.length);
        System.out.println(Arrays.toString(b));
        System.out.println(Arrays.toString(c));
        ByteBuffer buffer=ByteBuffer.allocate(a.length);
        buffer.put(Arrays.copyOfRange(a,0,3));
        System.out.println(Arrays.toString(buffer.array()));
        buffer.put(Arrays.copyOfRange(a,3,5));
        System.out.println(Arrays.toString(buffer.array()));

        MyVpnService.ArrayReplace(a,2,new byte[]{33,34});

        System.out.println(Arrays.toString(a));

    }
    @Test
    public void bitToStringTest(){
        byte[] a = {69,96,55};
        BinaryCodec codec = new BinaryCodec();

        String s = BinaryCodec.toAsciiString(a);
        System.out.println(s);
        byte[] fliped = flip(a);
        System.out.println(BinaryCodec.toAsciiString(fliped));
        System.out.println(Integer.toOctalString(65535));
        System.out.println(Integer.toOctalString(55));
        System.out.println(Integer.toBinaryString(~0b0110 & 0xFFFF));
        System.out.println((byte)255);
        byte n = -128;
        System.out.println((int)n);
    }
    @Test
    public void intToBit(){
        byte[] a = getByteFromInt(255,1);
        assertArrayEquals(new byte[]{(byte) 255},a);
        byte[] b = getByteFromInt(500,1);
        assertArrayEquals(new byte[]{1, -12},b);
        assertArrayEquals(new byte[]{1,(byte) 244},b);
        byte[] c = getByteFromInt(7777,0);
        assertArrayEquals(new byte[]{30,97},c);
        byte[] d = getByteFromInt(2,2);
        assertArrayEquals(new byte[]{0,2},d);
    }
    @Test
    public void byteToInt(){
        byte[] a = {20,-113};
        int b= ByteUtil.getIntValue(a);
        assertEquals(5263,b);
        byte[] a2 = {5,3,-108};
        int b2= ByteUtil.getIntValue(a2);
        assertEquals(328596,b2);
        TCPFlag flag = TCPFlag.fromByte((byte) 18);
        assertEquals(flag.ACK,1);
        assertEquals(flag.SYN,1);
        TCPFlag f2 = TCPFlag.SYN_ACK();
        assertEquals(18,f2.getByte());

    }
    @Test
    public void ipHeaderTest(){
        byte[] sIp = {10,0,0,1};
        byte[] dIp = {20,20,20,1};
        IPV4Header ipv4Header = new IPV4Header(sIp,dIp);
        ipv4Header.setFlag(TCPFlag.SYNOnly().getByte());
        ipv4Header.setFragmentOffset((short) 0);
        ipv4Header.setOptions(null);
        ipv4Header.setIdentification(new byte[]{2,5});
        ipv4Header.setProtocol((byte) 6);
        ipv4Header.setTimeToLive((byte) 20);
        ipv4Header.setTypeOfService((byte) 1);
        ipv4Header.setTotalLength(ByteUtil.getByteFromInt(40,2));
    }
    @Test
    public void hashTest(){
        String hash = DigestUtils.sha1Hex("mostafa");
        assertEquals("4755bfab4052cc27342fd251db714407b842eef3",hash);
    }
    @Test
    public void blockingQueueTest(){

    }
    public byte[] getByteFromInt(int value,int minByteArraySize){
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
    public byte[] flip(byte[] data){
        byte[] result = new byte[data.length];
        int index=0;
        for (int i=data.length-1;i>=0;i--){
            result[index]=data[i];
            index++;
        }
        return result;
    }
}