package ir.smartdevelopers.smarttunnel.ui.exceptions;

public class AuthFailedException extends Exception{
    public AuthFailedException() {
    }

    public AuthFailedException(String message) {
        super(message);
    }
}
