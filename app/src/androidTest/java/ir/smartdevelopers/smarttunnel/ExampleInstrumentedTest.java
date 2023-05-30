package ir.smartdevelopers.smarttunnel;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.net.ConnectivityManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() throws UnknownHostException, SocketException {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("ir.smartdevelopers.smarttunnel", appContext.getPackageName());
        ConnectivityManager manager = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        InetAddress[] addresses = InetAddress.getAllByName("google.com");
        Enumeration<NetworkInterface> neti= NetworkInterface.getNetworkInterfaces();
        for (InetAddress inadd : addresses){
            if (inadd instanceof Inet6Address){
//                if (inadd.isReachable(NetworkInterface.getByIndex()))
            }
        }
    }
}