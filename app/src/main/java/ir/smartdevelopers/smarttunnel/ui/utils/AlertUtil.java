package ir.smartdevelopers.smarttunnel.ui.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ir.smartdevelopers.smarttunnel.R;

public class AlertUtil {
    public enum Type {
        SUCCESS,ERROR,WARNING
    }
    public static void showToast(Context context,int messageRes,int duration,Type type){
        Toast toast = createToast(context,context.getString(messageRes),type);
        if (toast == null){
            return;
        }
        toast.setDuration(duration);
        toast.show();
    }
    public static void showToast(Context context,CharSequence message,int duration,Type type){
        Toast toast = createToast(context,message,type);
        if (toast == null){
            return;
        }
        toast.setDuration(duration);
        toast.show();
    }
    private static Toast createToast(Context context,CharSequence message,Type type){
        Toast toast = null;
        try {
             toast = new Toast(context);
            View view = LayoutInflater.from(context).inflate(R.layout.toast_view,null);
            TextView messageView = view.findViewById(android.R.id.message);
            messageView.setText(message);
            switch (type){
                case SUCCESS:
                    view.setBackgroundResource(R.drawable.toast_bg_success);
                    break;
                case WARNING:
                    view.setBackgroundResource(R.drawable.toast_bg_warning);
                    break;
                case ERROR:
                default:
                    view.setBackgroundResource(R.drawable.toast_bg_error);
                    break;
            }
            toast.setView(view);
        }catch (Exception e){
            toast = Toast.makeText(context,message,Toast.LENGTH_SHORT);
        }
        return toast;
    }
    public static void showAlertDialog(Context context,String message,String title,Type type){
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(R.string.ok,null);
        switch (type){
            case SUCCESS:
                builder.setIcon(R.drawable.ic_done_green);
                break;
            case WARNING:
                builder.setIcon(R.drawable.ic_warning);
                break;
            case ERROR:
                builder.setIcon(R.drawable.ic_error);
                break;
        }
        builder.create().show();
    }
    public static AlertDialog showLoadingDialog(Context context){
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setMessage(R.string.please_wait);
        builder.setView(new ProgressBar(context));
        return builder.show();
    }
}
