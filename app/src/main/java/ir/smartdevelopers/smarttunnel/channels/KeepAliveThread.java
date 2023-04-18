package ir.smartdevelopers.smarttunnel.channels;

public class KeepAliveThread extends Thread {
    private boolean mStop = false;
    private final int mIntervalSecond;
    private long triggerTime;
    private boolean mKeepAliveSent = false;
    private KeepAliveWorker mKeepAliveWorker;

    public KeepAliveThread( int intervalSecond,String name,KeepAliveWorker worker) {
        mIntervalSecond = intervalSecond;
        setName(name + "_keep-alive");
        mKeepAliveWorker = worker;
    }

    @Override
    public void run() {
        triggerTime = System.currentTimeMillis() + mIntervalSecond * 1000L;
        while (!mStop) {
            try {
                long sleep = triggerTime - System.currentTimeMillis();
                if (sleep > 0) {
                    Thread.sleep(sleep + 1);
                    continue;
                }
                if (mKeepAliveSent) {
                    break;
                }
                mKeepAliveSent = true;
                resetTimer(false);
                mKeepAliveWorker.doWork();
            } catch (InterruptedException ignore) {

            }
        }
        mKeepAliveWorker.onTimeOut();
    }

    public void resetTimer() {
        resetTimer(true);
    }

    private void resetTimer(boolean resetKeepAliveSentStatus) {
        if (resetKeepAliveSentStatus) {
            mKeepAliveSent = false;
        }
        triggerTime = System.currentTimeMillis() + mIntervalSecond * 1000L;
    }

    public void cancel() {
        mStop = true;
        triggerTime = 0;
    }
    public interface KeepAliveWorker{
        void doWork();
        void onTimeOut();
    }
}
