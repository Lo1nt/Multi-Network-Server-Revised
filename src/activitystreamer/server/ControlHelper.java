package activitystreamer.server;

import activitystreamer.util.Constant;
import activitystreamer.util.Message;
import activitystreamer.util.Settings;
import activitystreamer.util.User;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ControlHelper {
    private static final Logger log = LogManager.getLogger();
    private static ControlHelper controlHelper = null;
    private Control control;
    private Map<String, Integer> lockAllowedCount;
    private Map<String, Connection> lockRequestMap; // username, source port


    private ControlHelper() {
        control = Control.getInstance();
        lockAllowedCount = new ConcurrentHashMap<>();
        lockRequestMap = new ConcurrentHashMap<>();

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    for (Map.Entry<String, Long> entry : control.getLastAnnounceTimestamps().entrySet()) {
                        // if hasn't received SERVER_ANNOUNCE for 10 seconds，判定为超时，从server列表中移除
                        if (System.currentTimeMillis() - entry.getValue() > Settings.getActivityTimeout()) {
                            control.getOtherServers().remove(entry.getKey());
                            control.getLastAnnounceTimestamps().remove(entry.getKey());

                        }
                    }
                }
            }
        }).start();
    }


    public static ControlHelper getInstance() {
        if (controlHelper == null) {
            controlHelper = new ControlHelper();
        }
        return controlHelper;
    }


    public boolean process(String command, Connection con, JsonObject request) {
        switch (command) {
            case Message.INVALID_MESSAGE:
                return true;
            case Message.AUTHENTICATE:
                return authenticate(con, request);
            case Message.AUTHENTICATION_FAIL:
                return authenticationFail();
            case Message.REGISTER:
                return register(con, request);
            case Message.LOCK_REQUEST:
                return onLockRequest(con, request);
            case Message.LOCK_DENIED:
                return onLockDenied(con, request);
            case Message.LOCK_ALLOWED:
                return onLockAllowed(con, request);
            case Message.LOGIN:
                return login(con, request);
            case Message.LOGOUT:
                return logout(con);
            case Message.ACTIVITY_MESSAGE:
                return onReceiveActivityMessage(con, request);
            case Message.ACTIVITY_BROADCAST:
                return onReceiveActivityBroadcast(con, request);
            case Message.SERVER_ANNOUNCE:
                return onReceiveServerAnnounce(con, request);
            case Message.SYNCHRONIZE_USER:
                return synchronizeUser(request);
            default:
                return false;
        }
    }


    /**
     * Add all user info into externalRegisteredUsers.
     *
     * @param request
     * @return
     */
    private boolean synchronizeUser(JsonObject request) {
        Gson gson = new GsonBuilder().enableComplexMapKeySerialization().create();
        JsonObject users = (JsonObject) request.get("users");
        Type type = new TypeToken<Map<String, User>>() {
        }.getType();
        Map<String, User> map = gson.fromJson(users.toString(), type);
        control.getExternalRegisteredUsers().putAll(map);
//        log.debug("users === " + users);
        return false;
    }


    private boolean authenticate(Connection con, JsonObject request) {
        if (request.get("secret") == null) {
            return Message.invalidMsg(con, "the received message did not contain a secret");
        }
        String secret = request.get("secret").getAsString();
        if (!secret.equals(Settings.getServerSecret())) {
            // if the secret is incorrect
            return Message.authenticationFail(con, "the supplied secret is incorrect: " + secret);
        } else if (control.getConnections().contains(con) && con.isAuthenticated()) {
            return Message.invalidMsg(con, "the server has already successfully authenticated");
        }
        // TODO ack of successfully authenticate
        // No reply if the authentication succeeded.
        con.setAuthenticated(true);
        con.setName(Connection.CHILD);

        // synchronize all registered users to the new server
        Map<String, User> externalUsers = new ConcurrentHashMap<>();
        externalUsers.putAll(control.getLocalRegisteredUsers());
        externalUsers.putAll(control.getExternalRegisteredUsers());
        Message.synchronizeUser(con, externalUsers);

        con.setConnID(Constant.serverID);
        Constant.serverID += 2;
        // why
        Constant.clientID -= 2;
        return false;
    }


    private boolean authenticationFail() {
        return true;
    }


    private boolean onReceiveServerAnnounce(Connection con, JsonObject request) {
        if (request.get("id") == null) {
            return Message.invalidMsg(con, "message doesn't contain a server id");
        }
        relayMessage(con, request);

        // 记录是从child还是parent传来的
        request.addProperty("is_subtree", con.getName().equals(Connection.CHILD));

        control.getOtherServers().put(request.get("id").getAsString(), request);
        // record each server's timestamp of last SERVER_ANNOUNCE
        control.getLastAnnounceTimestamps().put(request.get("id").getAsString(), System.currentTimeMillis());

//        log.debug(request.get("port").getAsInt());
//        log.debug(otherServers);
//        log.debug(lastAnnounceTimestamps);
        return false;
    }


    private boolean register(Connection con, JsonObject request) {
        if (!request.has("username") || !request.has("secret")) {
            Message.invalidMsg(con, "The message is incorrect");
            return true;
        }

        String username = request.get("username").getAsString();
        String secret = request.get("secret").getAsString();

        if (con.isLoggedIn()) {
            return Message.registerFailed(con, " already logged in the system"); // true
        }

        // If username is registered locally or externally, then fail
        if (control.getLocalRegisteredUsers().containsKey(username)
                || control.getExternalRegisteredUsers().containsKey(username)) {
            return Message.registerFailed(con, username + " is already registered with the system"); // true
        } else {
            control.addToBeRegisteredUser(request, con);
            lockAllowedCount.put(username, 0);
            if (control.getOtherServers().size() == 0) { // if single server in the system
                control.addLocalRegisteredUser(username, secret);
                return Message.registerSuccess(con, "register success for " + username);
            }
            // relayMessage LOCK_REQUEST to all servers it connects
            for (Connection c : control.getConnections()) {
                if (c.getName().equals(Connection.PARENT) || c.getName().equals(Connection.CHILD)) {
                    Message.lockRequest(c, username, secret);
                }
            }
        }

        return false;
    }

    /**
     * Check local registered users list.
     * If username exists, LOCK_DENIED; else, LOCK_ALLOWED
     *
     * @param con
     * @param request
     * @return
     */
    private boolean onLockRequest(Connection con, JsonObject request) {
        if (!con.getName().equals(Connection.PARENT) && !con.getName().equals(Connection.CHILD)) {
            return Message.invalidMsg(con, "The connection has not authenticated");
        }
        String username = request.get("username").getAsString();
        String secret = request.get("secret").getAsString();

        lockRequestMap.put(username, con);

        if (control.getLocalRegisteredUsers().containsKey(username)) { // locally DENIED
            // send LOCK_DENIED to its connected servers
            for (Connection c : control.getConnections()) {
                if (c.getName().equals(Connection.PARENT) || c.getName().equals(Connection.CHILD)) {
                    return Message.lockDenied(con, username, secret);
                }
            }
        } else { // locally ALLOWED
            // temporarily add user info to externalUsers
            control.addExternalRegisteredUser(username, secret);

            // relay LOCK_REQUEST to other servers, except which it comes from
            relayMessage(con, request);

            // only send LOCK_ALLOWED to con (which LOCK_REQUEST comes from)
            return Message.lockAllowed(con, username, secret);
        }
        return false;
    }


    private boolean onLockAllowed(Connection con, JsonObject request) {
        if (!con.getName().equals(Connection.PARENT) && !con.getName().equals(Connection.CHILD)) {
            return Message.invalidMsg(con, "The connection has not authenticated");
        }
        String username = request.get("username").getAsString();
        int count = 0;
        if (lockAllowedCount.get(username) != null) {
            count = lockAllowedCount.get(username);
        }
        lockAllowedCount.put(username, ++count); // local LOCK_ALLOWED count += 1

        // if originally registered server:
        // if has received LOCK_ALLOWED from all servers，then REGISTER_SUCCESS
        if (control.getOtherServers().size() == lockAllowedCount.get(username)) {
            for (JsonObject key : control.getToBeRegisteredUsers().keySet()) {
                Connection c = control.getToBeRegisteredUsers().get(key);
                if (c != null && username.equals(key.get("username").getAsString())) {
                    lockAllowedCount.remove(username);
                    control.addLocalRegisteredUser(username, key.get("secret").getAsString());
                    return Message.registerSuccess(c, "register success for " + username);
                }
            }
        }

        // if intermediate server:
        // relay LOCK_ALLOWED only to the server which LOCK_REQUEST comes from
        if (lockRequestMap.get(username) != null) {
            Connection src = lockRequestMap.get(username);
            src.writeMsg(request.getAsString());
        }

        return false;
    }


    private boolean onLockDenied(Connection con, JsonObject request) {
        if (!con.getName().equals(Connection.PARENT) && !con.getName().equals(Connection.CHILD)) {
            return Message.invalidMsg(con, "The connection has not authenticated");
        }
        String username = request.get("username").getAsString();

        // if originally registered server:
        // remove corresponding username in toBeRegisteredUsers，and send REGISTER_FAILED to this server
        for (Map.Entry<JsonObject, Connection> entry : control.getToBeRegisteredUsers().entrySet()) {
            if (username.equals(entry.getKey().get("username").getAsString())) {
                Connection c = entry.getValue();
                control.getToBeRegisteredUsers().remove(entry.getKey());
                lockAllowedCount.remove(username);
                Message.registerFailed(c, username + " is already registered with the system"); // true
            }
        }
        // if intermediate server:
        // remove corresponding username in externalUsers
        if (control.getExternalRegisteredUsers().containsKey(username)) {
            control.getExternalRegisteredUsers().remove(username);
        }
        // relay LOCK_DENIED to other servers (except the src server)
        // in order to remove username in externalRegisteredUsers
        relayMessage(con, request);
        return false;
    }


    private boolean login(Connection con, JsonObject request) {
        if (!request.has("username")) {
            return Message.invalidMsg(con, "missed username");
        }
        String username = request.get("username").getAsString();
        String secret = request.get("secret").getAsString();

        Map<String, User> localRegisteredUsers = control.getLocalRegisteredUsers();
        Map<String, User> externalRegisteredUsers = control.getExternalRegisteredUsers();
        if (username.equals("anonymous")
                || localRegisteredUsers.containsKey(username) && localRegisteredUsers.get(username).getSecret().equals(secret)
                || externalRegisteredUsers.containsKey(username) && externalRegisteredUsers.get(username).getSecret().equals(secret)) {
            con.setLoggedIn(true);
            Message.loginSuccess(con, "logged in as user " + request.get("username").getAsString());
            for (String key : control.getOtherServers().keySet()) {
                if (key != null && control.getLoad() - ((Long) control.getOtherServers().get(key).get("load").getAsLong()).intValue() >= 2) {
                    return Message.redirect(con, control.getOtherServers().get(key).get("hostname").getAsString(),
                            "" + control.getOtherServers().get(key).get("port").getAsInt());
                }
            }
            return false;
        } else if (!localRegisteredUsers.containsKey(username)) {
            return Message.loginFailed(con, "client is not registered with the server");
        } else if (localRegisteredUsers.containsKey(username)
                && !(localRegisteredUsers.get(username).getSecret().equals(secret))) {
            return Message.loginFailed(con, "attempt to login with wrong username");
        } else {
            return Message.loginFailed(con, "");
        }

    }


    private boolean logout(Connection con) {
        con.setLoggedIn(false);
        return true;
    }


    private boolean onReceiveActivityMessage(Connection con, JsonObject request) {
        long msgTimeMill = System.currentTimeMillis();
        if (!request.has("username")) {
            return Message.invalidMsg(con, "the message did not contain a username");
        }
        if (!request.has("secret")) {
            return Message.invalidMsg(con, "the message did not contain a secret");
        }
        if (!request.has("activity")) {
            return Message.invalidMsg(con, "the message did not contain an activity");
        }
        String username = request.get("username").getAsString();
        String secret = request.get("secret").getAsString();
        JsonObject activity = (JsonObject) request.get("activity");
        activity.addProperty("authenticated_user", username);

        if (!con.isLoggedIn()) {
            return Message.authenticationFail(con, "the user has not logged in yet");
        } else if (!(control.getLocalRegisteredUsers().containsKey(username) && control.getLocalRegisteredUsers().get(username).getSecret().equals(secret)) &&
                !(control.getExternalRegisteredUsers().containsKey(username) && control.getExternalRegisteredUsers().get(username).getSecret().equals(secret))) {
            // if user is neither registered locally or externally
            return Message.authenticationFail(con, "the username and secret do not match the logged in the user");
        }

        JsonObject broadcastAct = new JsonObject();
        broadcastAct.addProperty("command", Message.ACTIVITY_BROADCAST);
        broadcastAct.add("activity", activity);
        broadcastAct.addProperty("time", msgTimeMill);

        relayMessage(con, broadcastAct);
        clientBroadcast(broadcastAct);

        return false;

    }


    private boolean onReceiveActivityBroadcast(Connection con, JsonObject msg) {
        relayMessage(con, msg);
        clientBroadcast(msg);
        return false;
    }

    /**
     * Relay message to all servers it connects EXCEPT source server
     *
     * @param src
     * @param request
     */
    private void relayMessage(Connection src, JsonObject request) {
        for (Connection c : control.getConnections()) {
            if (c.getSocket().getInetAddress() != src.getSocket().getInetAddress()
                    && (c.getName().equals(Connection.PARENT) || c.getName().equals(Connection.CHILD))) {
                c.writeMsg(request.toString());
            }
        }
    }


    /**
     * send message to valid user
     *
     * @param broadcastAct
     */
    private void clientBroadcast(JsonObject broadcastAct) {
        long timeMill = broadcastAct.get("time").getAsLong();
        broadcastAct.remove("time");
        for (Connection c : Control.getInstance().getConnections()) {
            if (!c.getName().equals(Connection.PARENT) && !c.getName().equals(Connection.CHILD)
                    && c.isLoggedIn() && timeMill >= c.getConnTime()) {
                c.writeMsg(broadcastAct.toString());
            }
        }

    }

}
