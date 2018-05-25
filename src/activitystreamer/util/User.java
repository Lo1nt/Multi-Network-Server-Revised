package activitystreamer.util;

import java.io.Serializable;

public class User implements Serializable {
    private String userName;
    private String secret;

    public User(String userName, String secret) {
        this.userName = userName;
        this.secret = secret;
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
