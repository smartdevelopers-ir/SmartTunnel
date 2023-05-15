package ir.smartdevelopers.smarttunnel;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.xbill.DNS.DClass;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.Section;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

import ir.smartdevelopers.smarttunnel.databinding.ActivityTestMainBinding;


public class TestMainActivity extends AppCompatActivity {
    private static final int SMS_CONSENT_REQUEST = 2;  // Set to an unused request code
    private ActivityTestMainBinding mBinding;
    private ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityTestMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        mBinding.btnConnect.setOnClickListener(v -> {

            mExecutorService.execute(()->{
                try {
                    ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                    Network[] allNetworks = manager.getAllNetworks();
                   Network active = findActiveNetwork(manager);
                   String activeInterfaceName = manager.getLinkProperties(active).getInterfaceName();
                    InetAddress[] addresses = InetAddress.getAllByName("google.com");
                    NetworkInterface activeInterface = NetworkInterface.getByName(activeInterfaceName);
                    for (InetAddress inadd : addresses){
                        if (inadd instanceof Inet6Address){
                            if (inadd.isReachable(100)){
                                System.out.println("reched");
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            // DNS
            try {
                Record record=Record.newRecord(Name.fromString("s11.goolha.tk."), Type.A, DClass.IN);
                Record record6=Record.newRecord(Name.fromString("s11.goolha.tk."), Type.AAAA, DClass.IN);
                Message message = Message.newQuery(record);
                Message message6 = Message.newQuery(record6);

                Resolver r = new SimpleResolver("8.8.8.8");
////
                ExecutorService service=Executors.newSingleThreadExecutor();
                service.execute(()->{
                    try {
//
                        Message m=r.send(message);
                        Message m6 = r.send(message6);
                        String ip =m.getSectionArray(Section.ANSWER)[0].rdataToString();
                        String ip6 = m6.getSectionArray(Section.ANSWER)[0].rdataToString();
                        Log.d("TTT", "onCreate: ");
//                        Lookup lookup = new Lookup("s6.goolha.tk",Type.AAAA);
//                        lookup.setResolver(new SimpleResolver("8.8.8.8"));
//                        Record[] records = lookup.run();
//                        Log.v("TTT","ssss");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });






            } catch (Exception e) {
                e.printStackTrace();
            }
            String host = mBinding.edtHost.getText().toString();
            String port = mBinding.edtPort.getText().toString();
            String user = mBinding.edtUser.getText().toString();
            String pass = mBinding.edtPass.getText().toString();
            connect(host, Integer.parseInt(port), user, pass);

        });
        mBinding.btnSendCommand.setOnClickListener(v->{
//            writeToShell(mBinding.edtCommand.getText().toString());
            connectToUrl();
        });


    }
    private Network findActiveNetwork(ConnectivityManager manager){
        Network active=null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            active =manager.getActiveNetwork();
        }else {
            NetworkInfo activeInfo = manager.getActiveNetworkInfo();
            Network[] allNetworks = manager.getAllNetworks();
            for (Network network : allNetworks){
                if (manager.getNetworkInfo(network).toString().equals(activeInfo.toString())){
                    active = network;
                    break;
                }
            }
        }
        return active;
    }

    private void connectToUrl() {
        mExecutorService.execute(()->{
            try {
                Proxy proxy = new Proxy(Proxy.Type.SOCKS,new InetSocketAddress(1080));
                HttpsURLConnection connection = (HttpsURLConnection) new URL("https://api.ipify.org/").openConnection(proxy);
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                Log.v("TTT","================== Data From URL ==================\n");
                String inputLine;
                while ((inputLine = in.readLine()) != null)
                    Log.v("TTT",inputLine);
                in.close();

                Log.v("TTT","================== Data From URL ==================\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void writeToShell(String command){
//        mExecutorService.execute(()->{
//            try {
//                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outWriter));
//                writer.write(command);
//                writer.flush();
//                writer.newLine();
//                writer.flush();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        });
    }
    private void connect(String host, int port, String user, String pass) {
        Intent vpnServiceIntent=VpnService.prepare(getApplicationContext());
        if (vpnServiceIntent !=null){
            startActivityForResult(vpnServiceIntent,5);
            return;
        }
        Intent vpnService = new Intent(this,MyVpnService.class);
        vpnService.putExtra("host",host);
        vpnService.putExtra("port",port);
        vpnService.putExtra("user",user);
        vpnService.putExtra("pass",pass);
        startService(vpnService);


    }




}