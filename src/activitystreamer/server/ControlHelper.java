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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ControlHelper {
    private static final Logger log = LogManager.getLogger();
    private static ControlHelper controlHelper = null;
    private Control control;
    private Map<String, JsonObject> otherServers; // all external servers
    private Map<String, Integer> lockAllowedCount;

    private Map<String, Connection> lockRequestMap; // username, source port

//    private Map<String, Long> lastAnnounceTimestamps;

    private Map<Connection, List<Connection>> neighbors;


    private ControlHelper() {
        control = Control.getInstance();
        otherServers = new ConcurrentHashMap<>();
        lockAllowedCount = new ConcurrentHashMap<>();
        lockRequestMap = new ConcurrentHashMap<>();
        neighbors = new ConcurrentHashMap<>();

//        lastAnnounceTimestamps = new ConcurrentHashMap<>();

//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                while (true) {
//                    try {
//                        Thread.sleep(1000);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                    for (Map.Entry<String, Long> entry : lastAnnounceTimestamps.entrySet()) {
//                        // 如果超过10秒钟没有收到announce，判定为超时，从server列表中移除
//                        if (System.currentTimeMillis() - entry.getValue() > Settings.getActivityTimeout()) {
//                            otherServers.remove(entry.getKey());
//                            lastAnnounceTimestamps.remove(entry.getKey());
//                            log.debug(otherServers);
//                        }
//                    }
//                }
//            }
//        }).start();


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
            case Message.NEIGHBOR_ANNOUNCE:
                return onReceiveNeighborAnnounce(request);
            default:
                return false;
        }
    }

    private boolean onReceiveNeighborAnnounce(JsonObject request) {
        Gson gson = new GsonBuilder().enableComplexMapKeySerialization().create();
        JsonObject jo = (JsonObject) request.get("neighbors");
        Type type = new TypeToken<Map<Connection, List<Connection>>>() {
        }.getType();
        neighbors = gson.fromJson(jo.getAsString(), type);
        log.debug("neighbors === " + neighbors);
        return false;
    }

    private boolean synchronizeUser(JsonObject request) {
        Gson gson = new GsonBuilder().enableComplexMapKeySerialization().create();
        JsonObject users = (JsonObject) request.get("users");
        Type type = new TypeToken<Map<String, User>>() {
        }.getType();
        Map<String, User> map = gson.fromJson(users.toString(), type);
        control.getExternalRegisteredUsers().putAll(map);
        log.debug("users === " + users);
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

        // No reply if the authentication succeeded.
        con.setAuthenticated(true);
        con.setName(Connection.SERVER);

        // synchronize all registered users to the new server
        Map<String, User> externalUsers = new ConcurrentHashMap<>();
        externalUsers.putAll(control.getLocalRegisteredUsers());
        externalUsers.putAll(control.getExternalRegisteredUsers());
        Message.synchronizeUser(con, externalUsers);

        con.setConnID(Constant.serverID);
        Constant.serverID += 2;
        Constant.clientID -= 2;
        return false;
    }


    private boolean authenticationFail() {
        return true;
    }


    private boolean register(Connection con, JsonObject request) {
        if (!request.has("username") || !request.has("secret")) {
            Message.invalidMsg(con, "The message is incorrect");
            return true;
        }

        String username = request.get("username").getAsString();
        String secret = request.get("secret").getAsString();
        // If username is registered locally or externally, then fail
        if (control.getLocalRegisteredUsers().containsKey(username)
                || control.getExternalRegisteredUsers().containsKey(username)) {
            return Message.registerFailed(con, username + " is already registered with the system"); // true
        } else {
            control.addToBeRegisteredUser(request, con);
            lockAllowedCount.put(username, 0);
            if (otherServers.size() == 0) { // if single server in the system
                control.addLocalRegisteredUser(username, secret);
                return Message.registerSuccess(con, "register success for " + username);
            }
            // relayMessage LOCK_REQUEST to all servers it connects
            for (Connection c : control.getConnections()) {
                if (c.getName().equals(Connection.SERVER)) {
                    Message.lockRequest(c, username, secret);
                }
            }
        }

        if (con.isLoggedIn()) {
            return Message.registerFailed(con, username + " is already registered with the system"); // true
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
        if (!con.getName().equals(Connection.SERVER)) {
            return Message.invalidMsg(con, "The connection has not authenticated");
        }
        String username = request.get("username").getAsString();
        String secret = request.get("secret").getAsString();

        lockRequestMap.put(username, con);

        if (control.getLocalRegisteredUsers().containsKey(username)) { // locally DENIED
            // send LOCK_DENIED to its connected servers
            for (Connection c : control.getConnections()) {
                if (c.getName().equals(Connection.SERVER)) {
                    return Message.lockDenied(con, username, secret);
                }
            }
        } else { // locally ALLOWED
            // 暂时把user信息加入到externalUsers里
            control.addExternalRegisteredUser(username, secret);

            // relay LOCK_REQUEST to other servers, except which it comes from
            relayMessage(con, request);

            // only send LOCK_ALLOWED to con (which LOCK_REQUEST comes from)
            return Message.lockAllowed(con, username, secret);
        }
        return false;
    }


    private boolean onLockAllowed(Connection con, JsonObject request) {
        if (!con.getName().equals(Connection.SERVER)) {
            return Message.invalidMsg(con, "The connection has not authenticated");
        }
        String username = request.get("username").getAsString();
        int count = 0;
        if (lockAllowedCount.get(username) != null) {
            count = lockAllowedCount.get(username);
        }
        lockAllowedCount.put(username, ++count); // local LOCK_ALLOWED count += 1

        // 如果是register所在server
        // 如果收到了所有server发来的LOCK_ALLOWED，注册成功
        if (otherServers.size() == lockAllowedCount.get(username)) {
            for (JsonObject key : control.getToBeRegisteredUsers().keySet()) {
                Connection c = control.getToBeRegisteredUsers().get(key);
                if (c != null && username.equals(key.get("username").getAsString())) {
                    lockAllowedCount.remove(username);
                    control.addLocalRegisteredUser(username, key.get("secret").getAsString());
                    return Message.registerSuccess(c, "register success for " + username);
                }
            }
        }

        // 如果是中继server
        // 只对LOCK_REQUEST的来源转发LOCK_ALLOWED
        if (lockRequestMap.get(username) != null) {
            Connection src = lockRequestMap.get(username);
            src.writeMsg(request.getAsString());
        }

        return false;
    }


    private boolean onLockDenied(Connection con, JsonObject request) {
        if (!con.getName().equals(Connection.SERVER)) {
            return Message.invalidMsg(con, "The connection has not authenticated");
        }
        String username = request.get("username").getAsString();

        // 如果是register所在server:
        // 清空toBeRegisteredUsers对应的username，并向它发送REGISTER_FAILED
        for (Map.Entry<JsonObject, Connection> entry : control.getToBeRegisteredUsers().entrySet()) {
            if (username.equals(entry.getKey().get("username").getAsString())) {
                Connection c = entry.getValue();
                control.getToBeRegisteredUsers().remove(entry.getKey());
                lockAllowedCount.remove(username);
                Message.registerFailed(c, username + " is already registered with the system"); // true
            }
        }
        // 如果是中继server:
        // 把本地存储的externalUsers对应的username清空
        if (control.getExternalRegisteredUsers().containsKey(username)) {
            control.getExternalRegisteredUsers().remove(username);
        }
        // 将LOCK_DENIED转发给其他server（除了来源server）用以清空externalRegisteredUsers中对应的username
        relayMessage(con, request);
        return false;
    }


    private boolean onReceiveServerAnnounce(Connection con, JsonObject request) {
        if (request.get("id") == null) {
            return Message.invalidMsg(con, "message doesn't contain a server id");
        }
        otherServers.put(request.get("id").getAsString(), request);
//        lastAnnounceTimestamps.put((String) request.get("id"), System.currentTimeMillis()); // TODO CHECK: 将每个server的announce时间戳记录下来
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
            for (String key : otherServers.keySet()) {
                if (key != null && control.getLoad() - ((Long) otherServers.get(key).get("load").getAsLong()).intValue() >= 2) {
                    return Message.redirect(con, otherServers.get(key).get("hostname").getAsString(), "" + otherServers.get(key).get("port").getAsInt());
                }
            }
            return false;
        } else if (!localRegisteredUsers.containsKey(username)) {
            return Message.loginFailed(con, "client is not registered with the server");
        } else if (localRegisteredUsers.containsKey(username) && !(localRegisteredUsers.get(username).getSecret().equals(secret))) {
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


//        pass activity_message (will be transformed as ACTIVITY_BROADCAST) to next server


        relayMessage(con, broadcastAct);
        username = updateMessageQueue(broadcastAct);

        clientBroadcastFromQueue(username);
        
        /*
        for (Connection c : control.getConnections()) {
            if (c.getName().equals(Connection.SERVER) || c.isLoggedIn()) {
                Message.activityBroadcast(c, broadcastAct);
            }
        }
        */
        return false;

    }


    private boolean onReceiveActivityBroadcast(Connection con, JsonObject msg) {

//      continue pass this ACTIVITY_BROADCAST to other server
        relayMessage(con, msg);

        String username = updateMessageQueue(msg);

        clientBroadcastFromQueue(username);

      /*
      for (Connection c : control.getConnections()) {
          if ( (c.getSocket().getInetAddress() != con.getSocket().getInetAddress()
                  && (c.getName().equals(Connection.SERVER)) || (!c.getName().equals(Connection.SERVER) && c.isLoggedIn())) ) {
              c.writeMsg(msg.toJSONString());
          }
      }
      */
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
                    && c.getName().equals(Connection.SERVER)) {
                c.writeMsg(request.toString());
            }
        }
    }

    //    set up message queue for specific user and return username
    private String updateMessageQueue(JsonObject msg) {
//        String username = msg.get("username").getAsString();
        String username = msg.get("activity").getAsJsonObject().get("authenticated_user").getAsString(); //TODO check json null
        Constant.messageQueue.putIfAbsent(username, new ConcurrentLinkedQueue<>());
        Constant.messageQueue.get(username).offer(msg);
        return username;
    }


    //    clean message queue for specific user
    private void clientBroadcastFromQueue(String username) {
        while (!Constant.messageQueue.get(username).isEmpty()) {
            JsonObject broadcastAct = Constant.messageQueue.get(username).poll();

            for (Connection c : Control.getInstance().getConnections()) {
                if (!c.getName().equals(Connection.SERVER) && c.isLoggedIn()) {
                    c.writeMsg(broadcastAct.toString());
                }
            }
        }

    }

}
