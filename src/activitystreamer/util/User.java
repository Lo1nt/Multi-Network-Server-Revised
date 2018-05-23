package activitystreamer.util;

import java.io.Serializable;
import java.net.SocketAddress;

public class User implements Serializable {
    private SocketAddress localSocketAddress;
    private String userName;
    private String secret;

    public User(String userName, String secret) {
        this.userName = userName;
        this.secret = secret;
    }

    public User(SocketAddress socketAddress, String userName, String secret) {
        this.localSocketAddress = socketAddress;
        this.userName = userName;
        this.secret = secret;
    }

    public SocketAddress getLocalSocketAddress() {
        return localSocketAddress;
    }

    public void setLocalSocketAddress(SocketAddress localSocketAddress) {
        this.localSocketAddress = localSocketAddress;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
