package ir.smartdevelopers.smarttunnel.ui.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Executors;

import ir.smartdevelopers.smarttunnel.HostKeyRepo;
import ir.smartdevelopers.smarttunnel.ui.classes.AcceptAllHostRepo;
import ir.smartdevelopers.smarttunnel.ui.interfaces.OnCompleteListener;
import ir.smartdevelopers.smarttunnel.ui.models.Config;
import ir.smartdevelopers.smarttunnel.ui.models.SSHConfig;

public class ConfigsUtil {
    private static final String sshFolderName = "ssh";
    public static Config loadConfig(Context context,String id, String configType) throws IOException {
        File folder = new File(context.getFilesDir(),"configs");
        if (Objects.equals(configType, SSHConfig.CONFIG_TYPE)){
            File sshFolder = new File(folder,sshFolderName);
            File configFile = new File(sshFolder,id);
            if (configFile.exists()){
                try (FileInputStream in = new FileInputStream(configFile)){
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024*4];
                    int len;
                    while ((len = in.read(buffer)) != -1){
                        out.write(buffer,0,len);
                    }
                    String json = out.toString();
                    SSHConfig config =  new Gson().fromJson(json,SSHConfig.class);
                    config.setHostKeyRepo(new AcceptAllHostRepo());
                    if (config.getJumper() != null){
                        config.getJumper().getSSHConfig().setHostKeyRepo(new AcceptAllHostRepo());
                    }
                    return config;
                }
            }
        }
        return null;
    }
    public static boolean saveConfig(Context context,Config config) throws IOException {
        File configFile=null;
        String json = null;
        File folder = new File(context.getFilesDir(),"configs");
        if (Objects.equals(config.getType(), SSHConfig.CONFIG_TYPE)){
            File sshFolder = new File(folder,sshFolderName);
            if (!sshFolder.exists()){
                sshFolder.mkdirs();
            }
            configFile = new File(sshFolder,config.getId());
            json = new Gson().toJson(config,SSHConfig.class);
        }

        if (configFile != null && json != null){
            try(FileOutputStream out = new FileOutputStream(configFile)) {
                out.write(json.getBytes());
                return true;
            }
        }
        return false;
    }
    public static void exportConfig(String configJson, String configType, OutputStream dest, OnCompleteListener<Boolean> callback){
        Handler mainHandler=new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(()->{
            byte[] configTypeBytes = configType.getBytes(StandardCharsets.UTF_8);
            byte[] appNameBytes = "SmartTunnel".getBytes(StandardCharsets.UTF_8);
            int typeLength = configTypeBytes.length;
            int appNameLength = appNameBytes.length;
            try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(bos);
                ){

                dos.writeInt(appNameLength);
                dos.write(appNameBytes);
                dos.writeInt(typeLength);
                dos.write(configTypeBytes);
                byte[] encrypted = Util.encrypt(configJson);
                dos.write(encrypted);
                dest.write(bos.toByteArray());
                dest.flush();
                dest.close();
                mainHandler.post(()->callback.onComplete(true));
            }catch (Exception e){
                mainHandler.post(()->callback.onComplete(false));
            }


        });
    }
    /** After import completed it returns pair of config type and config json*/
    public static void importConfig(InputStream in, OnCompleteListener<Pair<String,String>> callback){
        Handler mainHandler=new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(()->{
            try(ByteArrayOutputStream out = new ByteArrayOutputStream();
                DataInputStream dis = new DataInputStream(in)){

                int appNameLength = dis.readInt();
                byte[] appNameBytes = new byte[appNameLength];
                dis.read(appNameBytes);
                String appName = new String(appNameBytes);
                if (!Objects.equals(appName,"SmartTunnel")){
                    mainHandler.post(()->callback.onComplete(null));
                    return;
                }
                int configTypeLength = dis.readInt();
                byte[] configTypeBytes = new byte[configTypeLength];
                dis.read(configTypeBytes);
                String configType = new String(configTypeBytes,StandardCharsets.UTF_8);
                int len;
                byte[] buffer=new byte[1024*4];
                while ((len = dis.read(buffer)) != -1){
                    out.write(buffer,0,len);
                }

                byte[] encrypted = out.toByteArray();
                String configJson = Util.decrypt(encrypted);
                mainHandler.post(()->{
                    callback.onComplete(new Pair<>(configType,configJson));
                });
            } catch (IOException e) {
                mainHandler.post(()->{
                    callback.onComplete(null);
                });
            }
        });
    }
}
