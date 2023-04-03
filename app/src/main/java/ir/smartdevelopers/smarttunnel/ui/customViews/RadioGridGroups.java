package ir.smartdevelopers.smarttunnel.ui.customViews;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.RadioButton;

public class RadioGridGroups extends GridLayout {
    private int mCheckedId = -1;
    public RadioGridGroups(Context context) {
        super(context);
    }

    public RadioGridGroups(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RadioGridGroups(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public RadioGridGroups(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // checks the appropriate radio button as requested in the XML file
        if (mCheckedId != -1) {
            setCheckStateForView(mCheckedId, true);

        }
    }
    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (child instanceof RadioButton){
            RadioButton btn = (RadioButton) child;
            if (btn.isChecked()){
                if (mCheckedId != -1){
                    setCheckStateForView(mCheckedId,false);
                }
                mCheckedId = btn.getId();
            }
            btn.setOnClickListener(v->{
                if (btn.isChecked()){
                    check(v.getId());
                }
            });
        }
        super.addView(child, index, params);
    }

    private void setCheckStateForView(int viewId, boolean checked) {
        View view = findViewById(viewId);
        if (view instanceof RadioButton){
            RadioButton btn = (RadioButton) view;
            btn.setChecked(checked);
        }
    }
    public void check(int id){
        boolean changed = mCheckedId != id;
        if (!changed){
            return;
        }
        if (mCheckedId != -1){
            View view = findViewById(mCheckedId);
            if (view instanceof RadioButton){
                ((RadioButton) view).setChecked(false);
            }
        }
        if (id !=-1){
            View view = findViewById(id);
            if (view instanceof RadioButton){
                ((RadioButton) view).setChecked(true);
            }
        }
       mCheckedId = id;
    }
    public int getCheckedId(){
        return mCheckedId;
    }
}
