package activitystreamer.utils;

public class Settings {
    
    private static String localHostname = "localhost";
    private static Integer localPort = 3780;
    
    private static String remoteHostname = null;
    private static Integer remotePort = null;
    
    private static String serverSecret = null;
    
    
    private static String userName = null;
    private static String secret = null;
    
    
    public static String getLocalHostname() {
        return localHostname;
    }
    public static void setLocalHostname(String localHostname) {
        Settings.localHostname = localHostname;
    }
    public static Integer getLocalPort() {
        return localPort;
    }
    public static void setLocalPort(Integer localPort) {
        Settings.localPort = localPort;
    }
    public static String getRemoteHostname() {
        return remoteHostname;
    }
    public static void setRemoteHostname(String remoteHostname) {
        Settings.remoteHostname = remoteHostname;
    }
    public static Integer getRemotePort() {
        return remotePort;
    }
    public static void setRemotePort(Integer remotePort) {
        Settings.remotePort = remotePort;
    }
    public static String getServerSecret() {
        return serverSecret;
    }
    public static void setServerSecret(String serverScret) {
        Settings.serverSecret = serverScret;
    }
    public static String getUserName() {
        return userName;
    }
    public static void setUserName(String userName) {
        Settings.userName = userName;
    }
    public static String getSecret() {
        return secret;
    }
    public static void setSecret(String secret) {
        Settings.secret = secret;
    }
    
    
    
    
}
