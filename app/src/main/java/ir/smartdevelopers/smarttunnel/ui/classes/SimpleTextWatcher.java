package ir.smartdevelopers.smarttunnel.ui.classes;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.CallSuper;

import com.google.android.material.textfield.TextInputLayout;

public abstract class SimpleTextWatcher implements TextWatcher {
    private final EditText mEditText;
    private final TextInputLayout mLayout;

    public SimpleTextWatcher(EditText editText, TextInputLayout layout) {
        mEditText = editText;
        mLayout = layout;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @CallSuper
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (mLayout != null){
            if (mLayout.isErrorEnabled()){
                mLayout.setErrorEnabled(false);
                mLayout.setError(null);
            }
        }
        onTextChanged(s == null ? "" : s);
    }
    public abstract void onTextChanged(CharSequence text);

    @Override
    public void afterTextChanged(Editable s) {

    }
}
