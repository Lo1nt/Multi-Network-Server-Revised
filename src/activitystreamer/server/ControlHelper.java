package activitystreamer.server;

import activitystreamer.util.Constant;
import activitystreamer.util.Message;
import activitystreamer.util.Settings;
import activitystreamer.util.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ControlHelper {
    private static final Logger log = LogManager.getLogger();
    private static ControlHelper controlHelper = null;
    private Control control = Control.getInstance();
    private static Map<String, JSONObject> serverList = new ConcurrentHashMap<>();
    private static Map<String, Integer> lockAllowedCount = new ConcurrentHashMap<>();

    private ControlHelper() {
    }

    public static ControlHelper getInstance() {
        if (controlHelper == null) {
            controlHelper = new ControlHelper();
        }
        return controlHelper;
    }

    public boolean process(String command, Connection con, JSONObject request) {
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
            default:
                return Message.invalidMsg(con, "unknown message");
        }
    }

    private boolean authenticate(Connection con, JSONObject request) {
        if (request.get("secret") == null) {
            return Message.invalidMsg(con, "the received message did not contain a secret");
        }
        String secret = (String) request.get("secret");
        if (!secret.equals(Settings.getServerSecret())) {
            // if the secret is incorrect
            return Message.authenticationFail(con, "the supplied secret is incorrect: " + secret);
        }
// TODO
//        else if (control.getConnections().contains(con)) {
//            return Message.invalidMsg(con, "the server has already successfully authenticated");
//        }
        // No reply if the authentication succeeded.
        con.setName(Connection.SERVER);

        con.setConnID(Constant.serverID);
        Constant.serverID += 2;
        Constant.clientID -= 2;              // TODO check
        return false;
    }


    private boolean authenticationFail() {
        return true;
    }


    private boolean register(Connection con, JSONObject request) {
        if (!request.containsKey("username") || !request.containsKey("secret")) {
            Message.invalidMsg(con, "The message is incorrect");
            return true;
        }

        String username = (String) request.get("username");
        String secret = (String) request.get("secret");
        if (control.getRegisteredUsers().containsKey(username) || control.getExternalRegisteredUsers().containsKey(username)) {
            return Message.registerFailed(con, username + " is already registered with the system"); // true
        } else {
            control.addToBeRegisteredUser(request, con);
            lockAllowedCount.put(username, 0);
            if (serverList.size() == 0) { // if single server in the system
                control.addRegisteredUser(username, secret);
                return Message.registerSuccess(con, "register success for " + username);
            }
            for (Connection c : control.getConnections()) {
                if (c.getName().equals(Connection.SERVER)) {
                    return Message.lockRequest(c, username, secret);
                }
            }
        }

        if (con.isLoggedIn()) {
            return Message.registerFailed(con, username + " is already registered with the system"); // true
        }
        return false;
    }


    private boolean onLockRequest(Connection con, JSONObject request) {
        if (!con.getName().equals(Connection.SERVER)) {
            return Message.invalidMsg(con, "The connection has not authenticated");
        }
        String username = (String) request.get("username");
        String secret = (String) request.get("secret");

        if (control.getRegisteredUsers().containsKey(username)) { // DENIED
            for (Connection c : control.getConnections()) {
                if (c.getName().equals(Connection.SERVER)) {
                    return Message.lockDenied(con, username, secret);
                }
            }
        } else { // ALLOWED
            serverBroadcast(con, request);
            control.addExternalRegisteredUser(username, secret);
            for (Connection c : control.getConnections()) {
                if (c.getName().equals(Connection.SERVER)) {
                    return Message.lockAllowed(con, username, secret);
                }
            }
        }
        return false;
    }


    private boolean onLockAllowed(Connection con, JSONObject request) {
        if (!con.getName().equals(Connection.SERVER)) {
            return Message.invalidMsg(con, "The connection has not authenticated");
        }
        String username = (String) request.get("username");
        int count = 0;
        if (lockAllowedCount.get(username) != null) {
            count = lockAllowedCount.get(username);
        }
        lockAllowedCount.put(username, ++count);
        if (serverList.size() == lockAllowedCount.get(username)) {
            for (JSONObject key : control.getToBeRegisteredUsers().keySet()) {
                Connection c = control.getToBeRegisteredUsers().get(key);
                if (c != null && username.equals(key.get("username"))) {
                    lockAllowedCount.remove(username);
                    control.addRegisteredUser(username, (String) key.get("secret"));
                    return Message.registerSuccess(c, "register success for " + username);
                }
            }
        }

        serverBroadcast(con, request);
        return false;
    }


    private boolean onLockDenied(Connection con, JSONObject request) {
        if (!con.getName().equals(Connection.SERVER)) {
            return Message.invalidMsg(con, "The connection has not authenticated");
        }
        String username = (String) request.get("username");
        if (control.getExternalRegisteredUsers().containsKey(username)) {
            control.getExternalRegisteredUsers().remove(username);
        }
        for (Map.Entry<JSONObject, Connection> entry : control.getToBeRegisteredUsers().entrySet()) {
            if (username.equals(entry.getKey().get("username"))) {
                Connection c = entry.getValue();
                control.getToBeRegisteredUsers().remove(entry.getKey());
                lockAllowedCount.remove(username);
                Message.registerFailed(c, username + " is already registered with the system"); // true
            }
        }
        serverBroadcast(con, request);
        return false;
    }


    private boolean onReceiveServerAnnounce(Connection con, JSONObject request) {
        if (request.get("id") == null) {
            return Message.invalidMsg(con, "message doesn't contain a server id");
        }
        serverList.put((String) request.get("id"), request);
//        log.info(request.get("port") + " load: " + request.get("load"));
        serverBroadcast(con, request);
        return false;

    }


    private boolean login(Connection con, JSONObject request) {
        if (!request.containsKey("username")) {
            return Message.invalidMsg(con, "missed username");
        }
        String username = (String) request.get("username");
        String secret = (String) request.get("secret");

        Map<String, User> registeredUsers = control.getRegisteredUsers();
        Map<String, User> externalRegisteredUsers = control.getExternalRegisteredUsers();
        if (username.equals("anonymous")
                || registeredUsers.containsKey(username) && registeredUsers.get(username).getSecret().equals(secret)
                || externalRegisteredUsers.containsKey(username) && externalRegisteredUsers.get(username).getSecret().equals(secret)) {
            con.setLoggedIn(true);
            Message.loginSuccess(con, "logged in as user " + request.get("username"));
            for (String key : serverList.keySet()) {
                log.warn(control.getLoad() + " : " + serverList.get(key).get("load"));
                if (key != null && control.getLoad() - ((Long) serverList.get(key).get("load")).intValue() >= 2) {
                    return Message.redirect(con, (String) serverList.get(key).get("hostname"), "" + serverList.get(key).get("port"));
                }
            }
            return false;
        } else if (!registeredUsers.containsKey(username)) {
            return Message.loginFailed(con, "client is not registered with the server");
        } else if (registeredUsers.containsKey(username) && !(registeredUsers.get(username).getSecret().equals(secret))) {
            return Message.loginFailed(con, "attempt to login with wrong username");
        } else {
            return Message.loginFailed(con, "");
        }

    }


    private boolean logout(Connection con) {
        con.setLoggedIn(false);
        return true;
    }


    private boolean onReceiveActivityMessage(Connection con, JSONObject request) {
        if (!request.containsKey("username")) {
            return Message.invalidMsg(con, "the message did not contain a username");
        }
        if (!request.containsKey("secret")) {
            return Message.invalidMsg(con, "the message did not contain a secret");
        }
        if (!request.containsKey("activity")) {
            return Message.invalidMsg(con, "the message did not contain an activity");
        }
        String username = (String) request.get("username");
        String secret = (String) request.get("secret");
        JSONObject activity = (JSONObject) request.get("activity");
        activity.put("authenticated_user", username);

        if (!con.isLoggedIn()) {
            return Message.authenticationFail(con, "the user has not logged in yet");
        } else if (!(control.getRegisteredUsers().containsKey(username) && control.getRegisteredUsers().get(username).getSecret().equals(secret)) &&
                !(control.getExternalRegisteredUsers().containsKey(username) && control.getExternalRegisteredUsers().get(username).getSecret().equals(secret))) {
            // if user is neither registered locally or externally
            return Message.authenticationFail(con, "the username and secret do not match the logged in the user");
        }

        JSONObject broadcastAct = new JSONObject();
        broadcastAct.put("command", Message.ACTIVITY_BROADCAST);
        broadcastAct.put("activity", activity);

//        pass activity_message (will be transformed as ACTIVITY_BROADCAST) to next server
        serverBroadcast(con, broadcastAct);
        
        username = updateMessageQueue(request);
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

    
    private boolean onReceiveActivityBroadcast(Connection con, JSONObject msg) {
        
//      continue pass this ACTIVITY_BROADCAST to other server
      serverBroadcast(con, msg);
      
      
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
    
    
//    pass info to servers except server who sends request to this
    private void serverBroadcast(Connection con, JSONObject request) {
        for (Connection c : control.getConnections()) {
            if (c.getSocket().getInetAddress() != con.getSocket().getInetAddress()
                    && c.getName().equals(Connection.SERVER)) {
                c.writeMsg(request.toJSONString());
            }
        }
    }
    
//    set up message queue for specific user and return username
    private String updateMessageQueue(JSONObject msg) {
        String username = (String) msg.get("username");
        Constant.messageQueue.putIfAbsent(username, new ConcurrentLinkedQueue<JSONObject>());
        Constant.messageQueue.get(username).offer(msg);
        return username;
    }
    
    
//    clean message queue for specific user
    private void clientBroadcastFromQueue(String username) {
        while(!Constant.messageQueue.get(username).isEmpty()) {
            JSONObject broadcastAct = Constant.messageQueue.get(username).poll();
            
            for (Connection c: Control.getInstance().getConnections()) {
                if (!c.getName().equals(Connection.SERVER) && c.isLoggedIn()) {
                    c.writeMsg(broadcastAct.toJSONString());
                }
            }
        }
        
    }

}
