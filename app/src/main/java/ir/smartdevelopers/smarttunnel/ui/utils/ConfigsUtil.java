package ir.smartdevelopers.smarttunnel.ui.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Executors;

import ir.smartdevelopers.smarttunnel.ui.classes.AcceptAllHostRepo;
import ir.smartdevelopers.smarttunnel.ui.exceptions.ConfigNotSupportException;
import ir.smartdevelopers.smarttunnel.ui.interfaces.OnCompleteListener;
import ir.smartdevelopers.smarttunnel.ui.models.Config;
import ir.smartdevelopers.smarttunnel.ui.models.OpenVpnConfig;
import ir.smartdevelopers.smarttunnel.ui.models.SSHConfig;

public class ConfigsUtil {
    private static final String sshFolderName = "ssh";
    private static final String openVPNFolderName = "openvpn";
    public static Config loadConfig(Context context,String id, String configType) throws IOException {
        File folder = getConfigsFolder(context);
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
        } else if (Objects.equals(configType, OpenVpnConfig.CONFIG_TYPE)) {
            File openvpnFolder = new File(folder,openVPNFolderName);
            File configFile = new File(openvpnFolder,id);
            if (configFile.exists()){
                try (FileInputStream in = new FileInputStream(configFile)){
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024*4];
                    int len;
                    while ((len = in.read(buffer)) != -1){
                        out.write(buffer,0,len);
                    }
                    String json = out.toString();
                    return OpenVpnConfig.fromJson(json);
                }
            }
        }
        return null;
    }
    public static boolean saveConfig(Context context,Config config) throws IOException {
        File configFile=null;
        String json = null;
        File folder = getConfigsFolder(context);
        if (Objects.equals(config.getType(), SSHConfig.CONFIG_TYPE)){
            File sshFolder = new File(folder,sshFolderName);
            if (!sshFolder.exists()){
                sshFolder.mkdirs();
            }
            configFile = new File(sshFolder,config.getId());
            json = new Gson().toJson(config);
        } else if (Objects.equals(config.getType(), OpenVpnConfig.CONFIG_TYPE)) {
            File openvpnFolder = new File(folder,openVPNFolderName);
            if (!openvpnFolder.exists()){
                openvpnFolder.mkdirs();
            }
            configFile = new File(openvpnFolder,config.getId());
            json = new Gson().toJson(config);
        }

        if (configFile != null && json != null){
            try(FileOutputStream out = new FileOutputStream(configFile)) {
                out.write(json.getBytes());
                return true;
            }
        }
        return false;
    }
    public static File getConfigsFolder(Context context){
        return  new File(context.getFilesDir(),"configs");
    }
    public static File getConfigFolder(Context context,String configType){
        File folder = getConfigsFolder(context);
        File configFolder = null;
        if (Objects.equals(configType, SSHConfig.CONFIG_TYPE)) {
            configFolder = new File(folder, sshFolderName);
        }else if (Objects.equals(configType, OpenVpnConfig.CONFIG_TYPE)){
            configFolder = new File(folder, openVPNFolderName);
        }
        return configFolder;
    }
    public static void exportConfig(String configJson, String configType, OutputStream dest, OnCompleteListener<Boolean> callback){
        Handler mainHandler=new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(()->{
            byte[] configTypeBytes = configType.getBytes(StandardCharsets.UTF_8);
            byte[] appNameBytes = "SmartTunnel".getBytes(StandardCharsets.UTF_8);
            byte typeLength = (byte) configTypeBytes.length;
            byte appNameLength = (byte) appNameBytes.length;
            try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(bos);
                ){

                dos.write(appNameLength);
                dos.write(appNameBytes);
                dos.write(typeLength);
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
    public static void importConfig(InputStream in, OnCompleteListener<Pair<String,String>> callback) {
        Handler mainHandler=new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() ->{
            try(ByteArrayOutputStream out = new ByteArrayOutputStream();
                DataInputStream dis = new DataInputStream(in)){

                int appNameLength = dis.read() & 0xFF;
                byte[] appNameBytes = new byte[appNameLength];
                dis.read(appNameBytes);
                String appName = new String(appNameBytes);
                if (!Objects.equals(appName,"SmartTunnel")){
                    mainHandler.post(()->callback.onException(new ConfigNotSupportException()));
                }
                int configTypeLength = dis.read() & 0xFF;
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
                    callback.onException(e);
                });
            }
        });
    }
}
