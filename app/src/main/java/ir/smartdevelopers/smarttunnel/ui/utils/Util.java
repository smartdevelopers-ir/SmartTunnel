package ir.smartdevelopers.smarttunnel.ui.utils;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.jcraft.jsch.jce.KeyPairGenECDSA;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.concurrent.Executors;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import ir.smartdevelopers.smarttunnel.ui.interfaces.OnCompleteListener;

public class Util {
    public static boolean contains(CharSequence holeText,CharSequence text){
        if (TextUtils.isEmpty(holeText)){
            return false;
        }
        if (text == null){
            return false;
        }
        return holeText.toString().toLowerCase().contains(text.toString().toLowerCase());
    }
    public static byte[] encrypt(String value){
        byte[] encrypted=null;
        try {
            String key = "adFElpssA51155asFGLo536Rt656zzpo";
            Key secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"AES");
            Cipher cipher = javax.crypto.Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE,secretKeySpec);
            encrypted = cipher.doFinal(value.getBytes());
        } catch (Exception e) {
            return null;
        }
        return encrypted;
    }
    public static String decrypt(byte[] data){
        byte[] decrypted=null;
        try {
            String key = "adFElpssA51155asFGLo536Rt656zzpo";
            Key secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE,secretKeySpec);
            decrypted = cipher.doFinal(data);
        } catch (Exception e) {
            return null;
        }
        return new String(decrypted, StandardCharsets.UTF_8);
    }

}
