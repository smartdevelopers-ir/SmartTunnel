package ir.smartdevelopers.smarttunnel.packet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import ir.smartdevelopers.smarttunnel.utils.ArrayUtil;
import ir.smartdevelopers.smarttunnel.utils.ByteUtil;

public class TCPOption {
    /** End of options list
     * Option-Length = 1 , Option-Data = -
     * */
    private static final byte KIND_EOL = 0;
    /**
     * No operation
     * This may be used to align option fields on 32-bit boundaries for better performance.
     * Option-Length = 1 , Option-Data = -
     * */
    private static final byte KIND_NO = 1;
    /** Maximum segment size
     * Option-Length = 4 , Option-Data = SS
     * */
    private static final byte KIND_MSS = 2;
    /** Window scale
     * Option-Length = 3 , Option-Data = S
     * */
    private static final byte KIND_WS = 3;
    /** Selective Acknowledgement permitted
     * Option-Length = 2 , Option-Data = -
     **/
    private static final byte KIND_SAP = 4;
    /** Selective ACKnowledgement
     * These first two bytes are followed by a list of 1–4 blocks being selectively acknowledged,
     * specified as 32-bit begin/end pointers.
     * Option-Length = N (10, 18, 26, or 34) , Option-Data = BBBB, EEEE, ...
     **/
    private static final byte KIND_SA = 5;
    /** Timestamp and echo of previous timestamp
     * Option-Length = 10 , Option-Data = TTTT, EEEE
     * */
    private static final byte KIND_TS = 8;

    private byte[] maximumSegmentSizeByte;
    private byte windowScale;
    private boolean selectiveAcknowledgmentPermitted;
    private byte[] selectiveAcknowledgment;
    private byte[] timeStamp;
    private byte[] options;

    public TCPOption(byte[] options) {
        this.options = options;
    }

    public TCPOption() {
    }

//    public TCPOption(int maximumSegmentSize, byte windowScale) {
//        this.maximumSegmentSize = maximumSegmentSize;
//        this.maximumSegmentSizeByte = ByteUtil.getByteFromInt(maximumSegmentSize,2);
//        this.windowScale = windowScale;
//    }

    public byte[] getBytes(){
        if (options != null ){
            return options;
        }
        return generateOptions();
    }

    private byte[] generateOptions() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
        if (maximumSegmentSizeByte != null){
            stream.write(KIND_MSS);
            stream.write(4);
            stream.write(maximumSegmentSizeByte);
        }
        if (windowScale != 0){
            stream.write(KIND_NO);
            stream.write(KIND_WS);
            stream.write(3);
            stream.write(windowScale);
        }
        if (selectiveAcknowledgmentPermitted){
            stream.write(KIND_NO);
            stream.write(KIND_NO);
            stream.write(KIND_SAP);
            stream.write(2);
        }
        if (selectiveAcknowledgment != null){
            stream.write(KIND_NO);
            stream.write(KIND_NO);
            stream.write(KIND_SA);
            stream.write(selectiveAcknowledgment.length + 2);
            stream.write(selectiveAcknowledgment);
        }
        if (timeStamp != null){
            stream.write(KIND_NO);
            stream.write(KIND_NO);
            stream.write(KIND_TS);
            stream.write(timeStamp);
        }

        } catch (IOException ignore) {

        }
        if (stream.toByteArray().length == 0){
            return null;
        }
        return stream.toByteArray();
    }

    public static TCPOption fromByte(byte[] option){
        TCPOption op = new TCPOption(option);
        int i=0;
        do {
            byte kind = option[i];
            byte optionLength = 0;
            switch (kind){
                case KIND_EOL:
                    optionLength = 1;
                    break;
                case KIND_NO:
                    optionLength = 1;;
                    break;
                case KIND_MSS:
                    optionLength = option[i+1];
                    op.maximumSegmentSizeByte = Arrays.copyOfRange(option,i+2,(i+ optionLength));

                    break;
                case KIND_WS:
                    optionLength = option[i+1];
                    op.windowScale = option[i+optionLength-1];
                    break;
                case KIND_SAP:
                    optionLength = option[i+1];
                    byte permitted = option[i+1];
                    op.selectiveAcknowledgmentPermitted = permitted == 1;
                    break;
                case KIND_SA:
                    optionLength = option[i+1];
                    op.selectiveAcknowledgment = Arrays.copyOfRange(option,i+2,(i+ optionLength));
                    break;
                case KIND_TS:
                    optionLength = option[i+1];
                    op.timeStamp = Arrays.copyOfRange(option,i+2,(i+ optionLength));
                    break;

            }
            i += optionLength;
        }while (i < option.length);

        return op;
    }

    public int getMaximumSegmentSize() {
        if (maximumSegmentSizeByte == null){
            return  0;
        }
        return ByteUtil.getIntValue(maximumSegmentSizeByte);
    }

    public void setMaximumSegmentSizeByte(byte[] maximumSegmentSizeByte) {
        this.maximumSegmentSizeByte = maximumSegmentSizeByte;
    }
    public void setMaximumSegmentSize(int maximumSegmentSize) {
        this.maximumSegmentSizeByte = ByteUtil.getByteFromInt(maximumSegmentSize,2);
    }
    public byte getWindowScale() {
        return windowScale;
    }

    public void setWindowScale(byte windowScale) {
        this.windowScale = windowScale;
    }

    public void setSelectiveAcknowledgmentPermitted(boolean selectiveAcknowledgmentPermitted) {
        this.selectiveAcknowledgmentPermitted = selectiveAcknowledgmentPermitted;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = ByteUtil.getByteFromLong(timeStamp,8);
    }

    public byte[] getTimeStamp() {
        return timeStamp;
    }

    public byte[] getSelectiveAcknowledgment() {
        return selectiveAcknowledgment;
    }
    public SACK[] getSelectiveAcknowledgmentIntValues(){
        if (selectiveAcknowledgment == null ){
            return null;
        }
        SACK[] values = new SACK[selectiveAcknowledgment.length/4];
        int index=0;
        for (int i = 0; i< selectiveAcknowledgment.length ; i+=8){
            SACK sack = new SACK();
            sack.leftEdge = ByteUtil.getIntValue(Arrays.copyOfRange(selectiveAcknowledgment,i,i+4));
            sack.rightEdge= ByteUtil.getIntValue(Arrays.copyOfRange(selectiveAcknowledgment,i+4,i+8));
            values[index] = sack;
            index++;
        }
        return values;
    }

    public void setSelectiveAcknowledgment(byte[] selectiveAcknowledgment) {
        this.selectiveAcknowledgment = selectiveAcknowledgment;
    }
    public void setSelectiveAcknowledgment(SACK[] sacks) {
        byte[] selectiveACK = new byte[sacks.length * 2 * 4];
        int index = 0;
        for (SACK sack : sacks){
            ArrayUtil.replace(selectiveACK,index,ByteUtil.getByteFromInt(sack.leftEdge,4));
            ArrayUtil.replace(selectiveACK,index+=4,ByteUtil.getByteFromInt(sack.rightEdge,4));
        }
        selectiveAcknowledgment = selectiveACK;
    }
    public boolean isSelectiveAcknowledgmentPermitted() {
        return selectiveAcknowledgmentPermitted;
    }
    public static class SACK {
        public int leftEdge;
        public int rightEdge;
    }
}
