package ir.smartdevelopers.smarttunnel.ui.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import ir.smartdevelopers.smarttunnel.R;
import ir.smartdevelopers.smarttunnel.ui.models.ConfigListModel;
import ir.smartdevelopers.smarttunnel.ui.models.LogItem;
import ir.smartdevelopers.smarttunnel.ui.models.ProxyType;

public class PrefsUtil {
    public static final String APP_PREFS_NAME = "smart_prefs";
    private static String[] DEFAULT_FORBIDDEN_APPS = {
            "com.farsitel.bazaar",
            "ir.divar",
            "com.myirancell",
            "mob.banking.android.sepah",
            "com.sheypoor.mobile",
            "net.iranet.isc.sotp",
            "ir.stsepehr.hamrahcard",//saap
            "com.pooyabyte.mb.android",
            "com.isc.bsinew",// saderat
            "app.rbmain.a",// robika
            "com.digikala",
            "com.sibche.aspardproject.app", // aap
            "ir.sep.sesoot", // 724
            "ir.hafhashtad.android780", // 780
            "com.bpm.sekeh", // sekeh
            "com.ada.mbank.mehr", // mehriran
            "com.bki.mobilebanking.android", // keshavarzi
            "com.pmb.mobile", // melat
            "com.sadadpsp.eva", // eva-bank meli
            "com.refahmobilepayment", // refah
            "com.postbank.mb", // postbank
            "com.parsmobapp", // parsian
            "com.farazpardazan.enbank", // novin bank
            "ir.mci.ecareapp", // hamrah man
            "com.samanpr.blu", // blu bank
            "ir.nasim", // bale
            "ir.eitaa.messenger", // eita
            "mobi.mmdt.ottplus", // soroush
            "net.igap", // igap
            "com.gapafzar.messenger", // gap
            "ir.rubx.bapp", // robikax
            "cab.snapp.passenger", // snapp
            "cab.snapp.driver", // snapp
            "com.snappbox.bikerapp", // snappbox
            "com.snapp_box.android", // snappbox
            "taxi.tap30.passenger", // tap30
            "taxi.tap30.driver", // tap30
            "org.rajman.traffic.tehran.navigator", // neshan
            "ir.balad", // balad
            "ir.balad", // balad

    };
    public static void addConfig(Context context,ConfigListModel model){
        SharedPreferences prefs = getGeneralPrefs(context);
        List<String> configsList = new ArrayList<>(prefs.getStringSet(ConfigListModel.PREFS_NAME, Collections.emptySet()));
        Gson gson = new Gson();
        String newModelJson = gson.toJson(model);
        boolean update = false;
        for (int i = 0;i<configsList.size();i++){
            String configJson = configsList.get(i);
            ConfigListModel conf = gson.fromJson(configJson,ConfigListModel.class);
            if (Objects.equals(conf.configId,model.configId)){
                configsList.set(i,newModelJson);
                update = true;
            }

        }
        if (!update) {
            configsList.add(newModelJson);
        }
        prefs.edit().putStringSet(ConfigListModel.PREFS_NAME,new HashSet<>(configsList)).apply();
        if (model.isSelected()){
            setSelectedConfig(context,model);
        }
    }
    public static void deleteConfig(Context context,ConfigListModel model){
        SharedPreferences prefs = getGeneralPrefs(context);
        Set<String> configsJsonList = new HashSet<>(prefs.getStringSet(ConfigListModel.PREFS_NAME, Collections.emptySet()));
        ConfigListModel selectedConfig = getSelectedConfig(context);
        if (Objects.equals(selectedConfig,model)){
            setSelectedConfig(context,null);
        }
        Gson gson = new Gson();
        Set<String> newConfigs = new HashSet<>();
        for (String json : configsJsonList){
            ConfigListModel config = gson.fromJson(json,ConfigListModel.class);
            if (Objects.equals(config,model)){
                // we do not add this to new list so it will be deleted
                continue;
            }
            newConfigs.add(gson.toJson(config));
        }
        prefs.edit().putStringSet(ConfigListModel.PREFS_NAME,newConfigs).apply();
    }
    public static List<ConfigListModel> getAllConfigs(Context context){
        SharedPreferences prefs = getGeneralPrefs(context);
        Set<String> configsJsonList = new HashSet<>(prefs.getStringSet(ConfigListModel.PREFS_NAME, Collections.emptySet()));
        Gson gson = new Gson();
        ConfigListModel selectedConfig = getSelectedConfig(context);
        List<ConfigListModel> configs = new ArrayList<>();
        for (String json : configsJsonList){
            ConfigListModel config = gson.fromJson(json,ConfigListModel.class);
            if (Objects.equals(selectedConfig,config)){
                config.setSelected(true);
            }
            configs.add(config);
        }
        Collections.sort(configs);
        return configs;
    }
    public static void addLog(Context context, LogItem... logItems){
        List<LogItem> currentLogs = getLogs(context);
        currentLogs.addAll(Arrays.asList(logItems));
        if (currentLogs.size() > LogItem.MAX_LOG_CACHE_SIZE){
            currentLogs.removeAll(currentLogs.subList(0,logItems.length));
        }
        SharedPreferences preferences = context.getSharedPreferences("logs",Context.MODE_PRIVATE);
        Set<String> logsJson = new HashSet<>();
        Gson gson = new Gson();
        for (LogItem item : currentLogs){
            logsJson.add(gson.toJson(item));
        }
        preferences.edit().putStringSet("log_items",logsJson).apply();
    }
    public static List<LogItem> getLogs(Context context){
        SharedPreferences preferences = context.getSharedPreferences("logs",Context.MODE_PRIVATE);
        List<String> currentLogsJson = new ArrayList<>(preferences.getStringSet("log_items",Collections.emptySet()));
        List<LogItem> logItems = new ArrayList<>();
        for (String json : currentLogsJson){
            LogItem item = LogItem.fromJson(json);
            if (item != null) {
                logItems.add(item);
            }
        }
        Collections.sort(logItems, new Comparator<LogItem>() {
            @Override
            public int compare(LogItem o1, LogItem o2) {
                return Long.compare(o1.timeStamp,o2.timeStamp);
            }
        });
        return logItems;
    }
    public static void clearLogs(Context context){
        SharedPreferences preferences = context.getSharedPreferences("logs",Context.MODE_PRIVATE);
        preferences.edit().clear().apply();
    }
    public static SharedPreferences getGeneralPrefs(Context context){
        return context.getSharedPreferences(APP_PREFS_NAME,Context.MODE_PRIVATE);
    }

    public static void setSelectedConfig(Context context,ConfigListModel model) {
        getGeneralPrefs(context).edit().putString("selected_config",
                model==null ? null : new Gson().toJson(model))
                .apply();
    }
    public static ConfigListModel getSelectedConfig(Context context){
        String selectedConfigJson = getGeneralPrefs(context).getString("selected_config","");
        if (TextUtils.isEmpty(selectedConfigJson)){
            return null;
        }
        return new Gson().fromJson(selectedConfigJson,ConfigListModel.class);
    }
    public static void setFirstTime(Context context , boolean firstTime){
        getGeneralPrefs(context).edit().putBoolean("first_time",firstTime).apply();
    }
    public static boolean isFirstTime(Context context ){
        return getGeneralPrefs(context).getBoolean("first_time",true);
    }
    public static Set<String> getSelectedApps(Context context){
        HashSet<String> apps = new HashSet<>(getGeneralPrefs(context).getStringSet("selected_apps",Collections.emptySet()));
        return apps;
    }
    public static void setSelectedApps(Context context,Set<String> apps){
        getGeneralPrefs(context).edit().putStringSet("selected_apps",apps).apply();
    }

    public static Set<String> getForbiddenApps(Context context){
        Set<String> forbiddenApps = new HashSet<>(getGeneralPrefs(context).getStringSet("forbidden_apps",Collections.emptySet()));
        forbiddenApps.addAll(Arrays.asList(DEFAULT_FORBIDDEN_APPS));
        return forbiddenApps;
    }
    public static void setForbiddenApps(Context context,Set<String> forbiddenApps){
        getGeneralPrefs(context).edit().putStringSet("forbidden_apps",forbiddenApps).apply();
    }
    public static boolean isAllowSelectedAppsEnabled(Context context){
        return getGeneralPrefs(context).getBoolean("allow_selected_apps",true);
    }
    public static void setAllowSelectedApps(Context context,boolean enable){
        getGeneralPrefs(context).edit().putBoolean("allow_selected_apps",enable).apply();
    }
    public static void setDNSName(Context context,String name){
        getGeneralPrefs(context).edit().putString("DNS_name",name).apply();
    }
    public static String getDNSName(Context context){
        return getGeneralPrefs(context).getString("DNS_name","Google");
    }
    public static String getDNS1(Context context){
        return getGeneralPrefs(context).getString("DNS1","8.8.8.8");
    }
    public static void setDNS1(Context context,String dns1){
        getGeneralPrefs(context).edit().putString("DNS1",dns1).apply();
    }
    public static String getDNS2(Context context){
        return getGeneralPrefs(context).getString("DNS2","8.8.4.4");
    }
    public static void setDNS2(Context context,String dns2){
        getGeneralPrefs(context).edit().putString("DNS2",dns2).apply();
    }
    public static void setGlobalProxyType(Context context,int type){
        getGeneralPrefs(context).edit().putInt("global_proxy_type",type).apply();
    }
    public static int getGlobalProxyType(Context context){
        return getGeneralPrefs(context).getInt("global_proxy_type", ProxyType.TYPE_NONE);
    }
    public static void saveGlobalProxy(Context context,String proxyJson){
        getGeneralPrefs(context).edit().putString("global_proxy",proxyJson).apply();
    }
    public static String loadGlobalProxy(Context context){
        return getGeneralPrefs(context).getString("global_proxy",null);
    }
    public static void saveConfigFilesDirectoryPath(Context context,String uri){
        getGeneralPrefs(context).edit().putString("config_directory_path",uri).apply();
    }
    public static String getConfigFilesDirectoryPath(Context context){
        return getGeneralPrefs(context).getString("config_directory_path",null);
    }
    public static boolean isConnectionSoundEnabled(Context context){
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.key_connection_sound),false);
    }
    public static boolean isPreferIpv6(Context context){
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.key_prefer_ipv6),false);
    }
    public static void setPreferIpv6(Context context,boolean prefer){
         PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putBoolean(context.getString(R.string.key_prefer_ipv6),false).apply();
    }
}
