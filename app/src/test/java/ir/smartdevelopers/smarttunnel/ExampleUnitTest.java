package ir.smartdevelopers.smarttunnel;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.BinaryCodec;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Test;
import org.xbill.DNS.Message;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

import ir.smartdevelopers.smarttunnel.packet.IPV4Header;
import ir.smartdevelopers.smarttunnel.packet.Packet;
import ir.smartdevelopers.smarttunnel.packet.PacketV4;
import ir.smartdevelopers.smarttunnel.packet.TCP;
import ir.smartdevelopers.smarttunnel.packet.TCPFlag;
import ir.smartdevelopers.smarttunnel.packet.TCPOption;
import ir.smartdevelopers.smarttunnel.packet.TCPPacketQueue;
import ir.smartdevelopers.smarttunnel.packet.TCPPacketWrapper;
import ir.smartdevelopers.smarttunnel.ui.exceptions.ConfigException;
import ir.smartdevelopers.smarttunnel.ui.exceptions.ConfigNotSupportException;
import ir.smartdevelopers.smarttunnel.ui.models.Config;
import ir.smartdevelopers.smarttunnel.ui.utils.Util;
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


        System.out.println(Arrays.toString(a));

    }
    @Test
    public void dateFormatTest(){
        Calendar calendar = Calendar.getInstance();
        String name = String.format("%tY-%tm-%td",calendar,calendar,calendar);
        assertEquals(name,"2023-05-01");
    }
    @Test
    public void intToByteTest(){
        int a = 200;
        byte b = (byte) a;
        int c = b & 0xFF;
        assertEquals(a,c);
        assertEquals(String.valueOf(a),String.valueOf(c));
        long time = System.currentTimeMillis();
        String s =String.format("%tH:%tM:%tS",time,time,time);
        System.out.println(s);
        String m ="1682457105,WAIT,,,,,,";
        String[] args = m.split(",",8);
        assertEquals(args.length,8);
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
    public void encryptionTest(){
        String s = "salam Checotri ?";
        byte[] encrypted = Util.encrypt(s);
        assertNotNull(encrypted);
        String decryoted = Util.decrypt(encrypted);
        assertEquals(s,decryoted);
    }
    @Test
    public void TCPOptionTest(){
        String hexBytes="020405b40103030801010402";
        byte[] data = getBytesFromHexString(hexBytes);
        TCPOption option = TCPOption.fromByte(data);
        assertEquals(option.getMaximumSegmentSize(),1460);
        assertEquals(option.getWindowScale(),8);
        TCPOption newOption1 = new TCPOption();
        assertNull(newOption1.getBytes());
        newOption1.setMaximumSegmentSize(1024);
        assertArrayEquals(newOption1.getBytes(),new byte[]{2,4,4,0});
        newOption1.setWindowScale((byte) 2);
        assertArrayEquals(newOption1.getBytes(),new byte[]{2,4,4,0,1,3,3,2});
        TCPOption.SACK sack = new TCPOption.SACK();
        sack.leftEdge = 200;
        sack.rightEdge = 300;
        newOption1.setSelectiveAcknowledgment(new TCPOption.SACK[]{sack});
        assertArrayEquals(newOption1.getBytes(),new byte[]{2,4,4,0,1,3,3,2,1,1,5,10,0,0,0,-56,0,0,1,44});



    }
    @Test
    public void tcpPacketQueueTest() throws InterruptedException {
        TCPPacketQueue queue = new TCPPacketQueue();
        Packet pk1 = new PacketV4(new IPV4Header(new byte[]{},new byte[]{}),new TCP(100,100),null);
        Packet pk2 = new PacketV4(new IPV4Header(new byte[]{},new byte[]{}),new TCP(200,200),null);
        TCPPacketWrapper wrapper1 = new TCPPacketWrapper(pk1);
        wrapper1.setSequenceNumber(100);
        wrapper1.setNextSequenceNumber(200);
        TCPPacketWrapper wrapper1_next = new TCPPacketWrapper(null);
        wrapper1_next.setSequenceNumber(200);;
        TCPPacketWrapper wrapper2 = new TCPPacketWrapper(pk2);
        wrapper2.setSequenceNumber(200);
        wrapper2.setNextSequenceNumber(300);
        int[] index ={0};
        CountDownLatch latch = new CountDownLatch(2);
        new Thread(()->{
            index[0]=1;
            queue.put(wrapper1);
            queue.put(wrapper1_next);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            index[0]=2;
            queue.put(wrapper2);
            latch.countDown();
        },"t1").start();
        new Thread(()->{
            try {
                TCPPacketWrapper wr1 = queue.poll();
                assertEquals(wr1,wrapper1);
                assertEquals(index[0],1);
                Thread.sleep(50);
                TCPPacketWrapper wr1_next = queue.peek();
                assertEquals(wr1_next,wrapper1_next);
                TCPPacketWrapper wr2 = queue.poll();
                assertEquals(wr2,wrapper2);
                assertEquals(index[0],2);
                latch.countDown();

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        },"t2").start();
        latch.await();
    }
    @Test
    public void waitForWindowSizeTest(){
        new Thread(()->{
            try {
                Thread.sleep(25000);
                mClientWindowSize = 500;
                synchronized (mWindowFullLock){
                    mWindowFullLock.notify();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        },"tt").start();
        time = System.currentTimeMillis();
        waitIfWindowIsFull();
    }
    final Object mWindowFullLock = new Object();
    int mClientWindowSize = 0;
    long time;
    public void waitIfWindowIsFull() {
        synchronized (mWindowFullLock){
            if (mClientWindowSize == 0){
                try {
                    mWindowFullLock.wait(10000);
                    System.out.println("wait time out after "+( (System.currentTimeMillis() - time)/1000));
                    if (mClientWindowSize == 0){
                        waitIfWindowIsFull();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
    @Test
    public void tcpFlagTest(){
        TCPFlag flag = new TCPFlag();
        flag.SYN = 1;
        flag.ACK = 1;
        assertEquals(flag.toString(),"[ACK][SYN]");
    }
    @Test
    public void threadExceptionTest() throws ConfigNotSupportException, InterruptedException {
        thrException();

    }
    public void thrException() throws ConfigNotSupportException, InterruptedException {
        Semaphore semaphore = new Semaphore(0);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    throw new ConfigNotSupportException();
                } catch (ConfigNotSupportException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        t.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
                try {
                    assertFalse(e.getCause() instanceof ConfigNotSupportException);
                } finally {
                    semaphore.release();

                }

            }
        });
        t.start();
        semaphore.acquire();
    }
    @Test
    public void configStateTest(){
        SimpleConfig config = new SimpleConfig("conf1","id1","test");
        config.userName = "ali";
        String confJson = new Gson().toJson(config);
        SimpleConfig fromJsonConf = new Gson().fromJson(confJson,SimpleConfig.class);
        assertEquals(fromJsonConf.getName(),"conf1");

        assertEquals(fromJsonConf.userName, "ali");
    }
    class SimpleConfigDeserializer implements JsonDeserializer<SimpleConfig>{

        @Override
        public SimpleConfig deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject object = json.getAsJsonObject();
            SimpleConfig config = context.deserialize(json, typeOfT);
            return new SimpleConfig(object.get("mName").getAsString(),
                    object.get("id").getAsString(),object.get("type").getAsString());
        }
    }

    @JsonAdapter(SimpleConfigDeserializer.class)
    class SimpleConfig extends Config {

        public String userName;
        public SimpleConfig(String name, String id, String type) {
            super(name, id, type);
        }

        @Override
        public void connect() throws ConfigException {

        }

        @Override
        public int getMainSocketDescriptor() {
            return -1;
        }

        @Override
        public void retry() {

        }

        @Override
        public void cancel() {

        }

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public ParcelFileDescriptor getFileDescriptor() {
            return null;
        }
    }

    @Test
    public void blockingQueueTest(){

        int a = Integer.MAX_VALUE;
        int b = a+1;
        System.out.println(a);
        System.out.println(b);
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
    private byte[] getBytesFromHexString(String hexString){
        byte[] bytes = new byte[hexString.length()/2];
        int index=0;
        for (int i =0; i< bytes.length;i++){
            bytes[i] = (byte) Integer.parseInt(hexString.substring(index,index+2),16);
            index+=2;
        }
        return bytes;
    }

    @Test
    public void semaphoreTest(){
        Semaphore semaphore = new Semaphore(0);
        int[] count = {0};
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }
    @Test
    public void dnsParseTest(){
        byte[] responseData = {66, -114, -127, -128, 0, 1, 0, 1, 0, 0, 0, 0, 3, 119, 119, 119, 6, 103, 111, 111, 103, 108, 101, 3, 99, 111, 109, 0, 0, 1, 0, 1, -64, 12, 0, 1, 0, 1, 0, 0, 1, 35, 0, 4, -114, -5};
        try {
            Message msg = new Message(responseData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Test
    public void dnsResolverTest(){

    }
}