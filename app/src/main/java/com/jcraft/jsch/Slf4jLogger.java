package com.jcraft.jsch;


import ir.smartdevelopers.smarttunnel.BuildConfig;

/**
 * JSch logger to log entries using the SLF4J framework
 */
public class Slf4jLogger implements Logger {



  public Slf4jLogger() {}

  @Override
  public boolean isEnabled(int level) {
    switch (level) {
      case Logger.DEBUG:
      case Logger.INFO:
        return BuildConfig.DEBUG;
      default:
        return true;
    }
  }

  @Override
  public void log(int level, String message) {
    log(level, message, null);
  }

  @Override
  public void log(int level, String message, Throwable cause) {
    if (!isEnabled(level)) {
      return;
    }
    switch (level) {
      case Logger.DEBUG:
        ir.smartdevelopers.smarttunnel.utils.Logger.logDebug(message);
        break;
      case Logger.INFO:
        ir.smartdevelopers.smarttunnel.utils.Logger.logInfo(message);
        break;
      case Logger.WARN:
        ir.smartdevelopers.smarttunnel.utils.Logger.logWarning(message);
        break;
      case Logger.ERROR:
      case Logger.FATAL:
        ir.smartdevelopers.smarttunnel.utils.Logger.logException(message, cause);
        break;
      default:
        ir.smartdevelopers.smarttunnel.utils.Logger.logException(message, cause);
        break;
    }
  }
}
