package ir.smartdevelopers.smarttunnel.ui.utils;

import android.content.Context;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Objects;

import ir.smartdevelopers.smarttunnel.HostKeyRepo;
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
                    config.setHostKeyRepo(new HostKeyRepo(context));
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
}
