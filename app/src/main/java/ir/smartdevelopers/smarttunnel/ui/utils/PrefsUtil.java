package ir.smartdevelopers.smarttunnel.ui.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import ir.smartdevelopers.smarttunnel.ui.models.ConfigListModel;

public class PrefsUtil {
    public static final String APP_PREFS_NAME = "smart_prefs";
    private static String[] DEFAULT_FORBIDDEN_APPS = {
            "com.farsitel.bazaar",
            "ir.divar",
            "com.myirancell",
            "mob.banking.android.sepah",
            "com.sheypoor.mobile",
            "net.iranet.isc.sotp",
            "ir.stsepehr.hamrahcard",
            "com.pooyabyte.mb.android",
            "com.isc.bsinew",

    };
    public static void addConfig(Context context,ConfigListModel model){
        SharedPreferences prefs = getGeneralPrefs(context);
        Set<String> configsList = new HashSet<>(prefs.getStringSet(ConfigListModel.PREFS_NAME, Collections.emptySet()));
        configsList.add(new Gson().toJson(model));
        prefs.edit().putStringSet(ConfigListModel.PREFS_NAME,configsList).apply();
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
    public static Set<String> getAllowedApps(Context context){
        HashSet<String> allowedApps = new HashSet<>(getGeneralPrefs(context).getStringSet("allowed_apps",Collections.emptySet()));
        Set<String> forbiddenApps = getForbiddenApps(context);
        for (String forbiddenApp : forbiddenApps){
            allowedApps.remove(forbiddenApp);
        }
        return allowedApps;
    }
    public static void setAllowedApps(Context context,Set<String> allowedApps){
        getGeneralPrefs(context).edit().putStringSet("allowed_apps",allowedApps).apply();
    }
    public static Set<String> getDisallowedApps(Context context){
        HashSet<String> disallowedApps = new HashSet<>(getGeneralPrefs(context).getStringSet("disallowed_apps",Collections.emptySet()));
        disallowedApps.addAll(getForbiddenApps(context));
        return disallowedApps;
    }
    public static void setDisallowedApps(Context context,Set<String> disallowedApps){
        getGeneralPrefs(context).edit().putStringSet("disallowed_apps",disallowedApps).apply();
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
        return getGeneralPrefs(context).getBoolean("allow_selected_apps",false);
    }
    public static String getDNS1(Context context){
        return getGeneralPrefs(context).getString("DNS1","");
    }
    public static void setDNS1(Context context,String dns1){
        getGeneralPrefs(context).edit().putString("DNS1",dns1).apply();
    }
    public static String getDNS2(Context context){
        return getGeneralPrefs(context).getString("DNS2","");
    }
    public static void setDNS2(Context context,String dns2){
        getGeneralPrefs(context).edit().putString("DNS2",dns2).apply();
    }
}
