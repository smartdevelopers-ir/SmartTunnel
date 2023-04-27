package ir.smartdevelopers.smarttunnel.ui.interfaces;

public abstract class OnCompleteListener<E> {
   public abstract void onComplete(E e);
   public void onException(Exception e){};
}
