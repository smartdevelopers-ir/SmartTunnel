package ir.smartdevelopers.smarttunnel.ui.interfaces;

import android.view.View;

public interface OnListItemClickListener<E> {
    void onItemClicked(View view,E e,int position);
}
