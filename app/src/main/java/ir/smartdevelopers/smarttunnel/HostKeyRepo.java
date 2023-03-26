package ir.smartdevelopers.smarttunnel;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.SparseArray;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.UserInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;
import java.util.Objects;

public class HostKeyRepo implements HostKeyRepository {
    private  final SparseArray<String> types = new SparseArray<>();
    private SharedPreferences mSharedPreferences;
    public HostKeyRepo(Context context) {
        mSharedPreferences = context.getSharedPreferences("known_host", Context.MODE_PRIVATE);
        types.put(HostKey.SSHRSA,"ssh-rsa");
        types.put(HostKey.SSHDSS,"ssh-dss");
        types.put(HostKey.ECDSA256,"ecdsa-sha2-nistp256");
        types.put(HostKey.ECDSA384,"ecdsa-sha2-nistp384");
        types.put(HostKey.ECDSA521,"ecdsa-sha2-nistp521");
    }

    @Override
    public int check(String host, byte[] key) {
        if (true){
            return OK;
        }
        Map<String, ?> map = mSharedPreferences.getAll();
        for (String mapKey : map.keySet()) {
            if (mapKey.startsWith(host)) {
                HostKey hostKey = generateHostKeyFromJson((String) map.get(mapKey));
                if (hostKey != null) {
                    if (Objects.equals(Base64.encodeToString(key,0).replace("\n",""),
                            hostKey.getKey().replace("\n",""))) {
                        return OK;
                    } else {
                        return CHANGED;
                    }
                }
            }
        }
        return NOT_INCLUDED;
    }

    @Override
    public void add(HostKey hostkey, UserInfo ui) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("host", hostkey.getHost());
            jsonObject.put("type", hostkey.getType());
            jsonObject.put("key", hostkey.getKey());
            jsonObject.put("comment", hostkey.getComment());

            mSharedPreferences.edit().putString(getPrefsKey(hostkey.getHost(), hostkey.getType())
                    , jsonObject.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void remove(String host, String type) {
        mSharedPreferences.edit().remove(getPrefsKey(host, type)).apply();
    }

    @Override
    public void remove(String host, String type, byte[] key) {
        mSharedPreferences.edit().remove(getPrefsKey(host, type)).apply();
    }

    @Override
    public String getKnownHostsRepositoryID() {
        return "smart_known_host_id_8585";
    }

    @Override
    public HostKey[] getHostKey() {
        Map<String, ?> map = mSharedPreferences.getAll();
        HostKey[] hostKeys = new HostKey[map.size()];

        int i = 0;
        for (String mapKey : map.keySet()) {
            String json = (String) map.get(mapKey);
            hostKeys[i] = generateHostKeyFromJson(json);

        }
        return hostKeys;
    }

    @Override
    public HostKey[] getHostKey(String host, String type) {
        String json = mSharedPreferences.getString(getPrefsKey(host, type), null);
        HostKey[] hostKeys;
        if (json != null) {
            HostKey hostKey = generateHostKeyFromJson(json);
            hostKeys = new HostKey[]{hostKey};
        } else {
            hostKeys = new HostKey[0];
        }
        return hostKeys;
    }

    private String getPrefsKey(String host, String type) {
        return host + "_" + type;
    }

    private HostKey generateHostKeyFromJson(String json) {
        HostKey hostKey = null;
        try {
            JSONObject jsonObject = new JSONObject(json);
            String host = getStringFromJson(jsonObject,"host");
            String key = getStringFromJson(jsonObject,"key");
            String comment = getStringFromJson(jsonObject,"comment");
            String typeString = getStringFromJson(jsonObject,"type");
            int typeIndex =  -1;
            for (int i = 0;i<types.size();i++){
                if (types.valueAt(i).equals(typeString)){
                    typeIndex=i;
                    break;
                }
            }
            int type = 0;
            if (typeIndex >=0 ){
                type = types.keyAt(typeIndex);
            }
            hostKey = new HostKey(host, type, Base64.decode(key,0), comment);
        } catch (JSONException | JSchException e) {
            e.printStackTrace();
        }
        return hostKey;
    }
    private String getStringFromJson(JSONObject data,String key){
        String value=null;
        try {
            value=data.getString(key);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return value;
    }
}
