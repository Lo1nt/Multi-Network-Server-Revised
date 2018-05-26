package activitystreamer.util;

import com.google.gson.JsonObject;
import activitystreamer.server.Connection;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.util.List;
import java.util.Map;

public class Message {
    public static final String AUTHENTICATE = "AUTHENTICATE";
    public static final String INVALID_MESSAGE = "INVALID_MESSAGE";
    public static final String AUTHENTICATION_FAIL = "AUTHENTICATION_FAIL";
    public static final String LOGIN = "LOGIN";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String REDIRECT = "REDIRECT";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String ACTIVITY_MESSAGE = "ACTIVITY_MESSAGE";
    public static final String SERVER_ANNOUNCE = "SERVER_ANNOUNCE";
    public static final String ACTIVITY_BROADCAST = "ACTIVITY_BROADCAST";
    public static final String REGISTER = "REGISTER";
    public static final String REGISTER_FAILED = "REGISTER_FAILED";
    public static final String REGISTER_SUCCESS = "REGISTER_SUCCESS";
    public static final String LOCK_REQUEST = "LOCK_REQUEST";
    public static final String LOCK_DENIED = "LOCK_DENIED";
    public static final String LOCK_ALLOWED = "LOCK_ALLOWED";
    public static final String SYNCHRONIZE_USER = "SYNCHRONIZE_USER";
    public static final String ACK = "ACK";
    public static final String BROADCASTSUCCESS = "BROADCASTSUCCESS";


    public synchronized static boolean synchronizeUser(Connection con, Map<String, User> users) {
        JsonObject json = new JsonObject();
        json.addProperty("command", Message.SYNCHRONIZE_USER);
        Gson gson = new GsonBuilder().enableComplexMapKeySerialization().create();
        json.add("users", new JsonParser().parse(gson.toJson(users)).getAsJsonObject());
        con.writeMsg(new Gson().toJson(json));
        return false;
    }

    public synchronized static void broadCastSuccess(Connection con, JsonObject msg) {
        JsonObject json = new JsonObject();
        json.addProperty("command", Message.BROADCASTSUCCESS);
        json.add("msg", msg);
        con.writeMsg(new Gson().toJson(json));
        return;
    }

    public synchronized static boolean returnAck(Connection con, JsonObject msg) {
        System.out.println("send ack");
        JsonObject json = new JsonObject();
        json.addProperty("command", Message.ACK);
        json.add("msg", msg);
        json.addProperty("from", Settings.getServerId());
        con.writeMsg(new Gson().toJson(json));
        return false;
    }

    public synchronized static boolean invalidMsg(Connection con, String info) {
        JsonObject json = new JsonObject();
        json.addProperty("command", Message.INVALID_MESSAGE);
        json.addProperty("info", info);
        con.writeMsg(new Gson().toJson(json));
        return true;
    }

    public synchronized static void authenticate(Connection con) {
        JsonObject json = new JsonObject();
        json.addProperty("command", Message.AUTHENTICATE);
        json.addProperty("secret", Settings.getServerSecret());
        con.writeMsg(new Gson().toJson(json));
    }

    public synchronized static boolean authenticationFail(Connection con, String info) {
        JsonObject json = new JsonObject();
        json.addProperty("command", Message.AUTHENTICATION_FAIL);
        json.addProperty("info", info);
        con.writeMsg(new Gson().toJson(json));
        return true;
    }


    public synchronized static void serverAnnounce(Connection con, int load, int parentCount, int childCount) {
        Gson gson = new Gson();
        JsonObject json = new JsonObject();
        json.addProperty("command", Message.SERVER_ANNOUNCE);
        json.addProperty("id", Settings.getServerId());
        json.addProperty("load", load);
        json.addProperty("parent_count", parentCount);
        json.addProperty("child_count", childCount);
        json.addProperty("hostname", Settings.getLocalHostname());
        json.addProperty("port", Settings.getLocalPort());
        json.addProperty("relay_count", 0);
        con.writeMsg(gson.toJson(json));
    }


    public synchronized static boolean lockRequest(Connection con, String username, String secret) {
        JsonObject json = new JsonObject();
        json.addProperty("command", Message.LOCK_REQUEST);
        json.addProperty("username", username);
        json.addProperty("secret", secret);
        con.writeMsg(new Gson().toJson(json));
        return false;
    }

    public synchronized static boolean lockDenied(Connection con, String username, String secret) {
        JsonObject json = new JsonObject();
        json.addProperty("command", Message.LOCK_DENIED);
        json.addProperty("username", username);
        json.addProperty("secret", secret);
        con.writeMsg(new Gson().toJson(json));
        return false;
    }

    public synchronized static boolean lockAllowed(Connection con, String username, String secret) {
        JsonObject json = new JsonObject();
        json.addProperty("command", Message.LOCK_ALLOWED);
        json.addProperty("username", username);
        json.addProperty("secret", secret);
        con.writeMsg(new Gson().toJson(json));
        return false;
    }

    public synchronized static boolean registerFailed(Connection con, String info) {
        JsonObject json = new JsonObject();
        json.addProperty("command", Message.REGISTER_FAILED);
        json.addProperty("info", info);
        con.writeMsg(new Gson().toJson(json));
        return true;
    }

    public synchronized static boolean registerSuccess(Connection con, String info) {
        JsonObject json = new JsonObject();
        json.addProperty("command", Message.REGISTER_SUCCESS);
        json.addProperty("info", info);
        con.writeMsg(new Gson().toJson(json));
        return false;
    }

    /**
     * Client register
     *
     * @param userName
     * @param secret
     * @return
     */
    public synchronized static String register(String userName, String secret) {
        JsonObject json = new JsonObject();
        json.addProperty("command", Message.REGISTER);
        json.addProperty("username", userName);
        json.addProperty("secret", secret);
        return new Gson().toJson(json);
    }

    /**
     * Client anonymous login
     *
     * @return
     */
    public synchronized static String login() {
        JsonObject json = new JsonObject();
        json.addProperty("command", Message.LOGIN);
        json.addProperty("username", Settings.getUsername());
        return new Gson().toJson(json);
    }

    /**
     * Client normal login
     *
     * @param userName
     * @return
     */
    public synchronized static String login(String userName) {
        JsonObject json = new JsonObject();
        json.addProperty("command", Message.LOGIN);
        json.addProperty("username", userName);
        json.addProperty("secret", Settings.getUserSecret());
        return new Gson().toJson(json);
    }

    public synchronized static boolean loginSuccess(Connection con, String info) {
        JsonObject json = new JsonObject();
        json.addProperty("command", Message.LOGIN_SUCCESS);
        json.addProperty("info", info);
        con.writeMsg(new Gson().toJson(json));
        return false;
    }

    public synchronized static boolean loginFailed(Connection con, String info) {
        JsonObject json = new JsonObject();
        json.addProperty("command", Message.LOGIN_FAILED);
        json.addProperty("info", info);
        con.writeMsg(new Gson().toJson(json));
        return true;
    }

    public synchronized static boolean redirect(Connection con, String hostname, String port) {
        JsonObject json = new JsonObject();
        json.addProperty("command", Message.REDIRECT);
        json.addProperty("hostname", hostname);
        json.addProperty("port", Integer.parseInt(port));
        con.writeMsg(new Gson().toJson(json));
        return true;
    }

    public synchronized static boolean activityBroadcast(Connection con, JsonObject activity) {
        con.writeMsg(new Gson().toJson(activity));
        return false;
    }

    public static JsonObject connCloseMsg() {
        JsonObject json = new JsonObject();
        StringBuilder sb = new StringBuilder();
        sb.append("connection closed to /")
                .append(Settings.getRemoteHostname())
                .append(":")
                .append(Settings.getRemotePort())
                .append(", please restart new connection");
        json.addProperty("info", sb.toString());
        return json;
    }

    public static JsonObject redirectMsg() {
        JsonObject json = new JsonObject();
        StringBuilder sb = new StringBuilder();
        sb.append("Start new connection to /");
        sb.append(Settings.getRemoteHostname())
                .append(":")
                .append(Settings.getRemotePort())
                .append(", please wait");
        json.addProperty("info", sb.toString());
        return json;
    }


}
