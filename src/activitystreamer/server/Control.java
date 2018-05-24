package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import activitystreamer.util.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Control extends Thread {
    private static final Logger log = LogManager.getLogger();
    private static List<Connection> connections;
    private static boolean term = false;
    private static Listener listener;

    private static Control control = null;
    private Map<String, User> localRegisteredUsers;
    private Map<JsonObject, Connection> toBeRegisteredUsers;
    private Map<String, User> externalRegisteredUsers;

    private int load;

    public static Control getInstance() {
        if (control == null) {
            control = new Control();
        }
        return control;
    }

    private Control() {
        // initialize the connections array
        connections = Collections.synchronizedList(new ArrayList<>());
        localRegisteredUsers = new ConcurrentHashMap<>();
        toBeRegisteredUsers = new ConcurrentHashMap<>();
        externalRegisteredUsers = new ConcurrentHashMap<>();

        // start a listener
        try {
            listener = new Listener();
        } catch (IOException e1) {
            log.fatal("failed to startup a listening thread: " + e1);
            System.exit(-1);
        }
        start();
    }

    public void initiateConnection() {
        // make a connection to another server if remote hostname is supplied
        if (Settings.getRemoteHostname() != null) {
            try {
                Connection c = outgoingConnection(new Socket(Settings.getRemoteHostname(), Settings.getRemotePort()));
            } catch (IOException e) {
                log.error("failed to make connection to " + Settings.getRemoteHostname() + ":"
                        + Settings.getRemotePort() + " :" + e);
                System.exit(-1);
            }
        }
    }

    /**
     * Processing incoming messages from the connection. Return true if the
     * connection should close.
     *
     * @param con
     * @param msg result JSON string
     * @return
     */
    public synchronized boolean process(Connection con, String msg) {
        JsonObject request;
        try {
            request = (JsonObject) new JsonParser().parse(msg);
        } catch (Exception e) {
            return Message.invalidMsg(con, "the received message is not in valid format");
        }

        if (request.get("command") == null) {
            return Message.invalidMsg(con, "the received message did not contain a command");
        }

        String command = request.get("command").getAsString();
        return ControlHelper.getInstance().process(command, con, request);
    }

    /**
     * The connection has been closed by the other party.
     *
     * @param con
     */
    public synchronized void connectionClosed(Connection con) {
        if (term) {
            return;
        }
        if (connections.contains(con)) {
            connections.remove(con);
        }

        if (!con.getName().equals(Connection.PARENT) && !con.getName().equals(Connection.CHILD)) {
            con.setLoggedIn(false);
        }

        // If parent server crashes，then establish new connection to another server (the one with minimum port number)
        if (con.getName().equals(Connection.PARENT)) {
            Map<String, JsonObject> otherServers = ControlHelper.getInstance().getOtherServers();

            // TODO there's a restriction: if the server with minimum port number crashes, then...
            int newRemotePort = minPortOfSystem(otherServers);
            if (newRemotePort != Settings.getLocalPort()) {
                Settings.setRemotePort(newRemotePort);
                initiateConnection();
            }
        }

    }

    private int minPortOfSystem(Map<String, JsonObject> otherServers) {
        String minPort = (String) CommonUtil.getMinKey(otherServers);
        return Integer.parseInt(minPort);
    }


    /**
     * A new incoming connection has been established, and a reference is returned
     * to it. 1. remote server -> local server 2. client -> local server
     *
     * @param s
     * @return
     * @throws IOException
     */
    public synchronized Connection incomingConnection(Socket s) throws IOException {
        log.debug("incoming connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s);
        c.setConnID(Constant.clientID);
        c.setConnTime(System.currentTimeMillis());
        Constant.clientID += 2;
        connections.add(c);
        return c;
    }

    /**
     * A new outgoing connection has been established, and a reference is returned to it.
     *
     * @param s
     * @return
     * @throws IOException
     */
    public synchronized Connection outgoingConnection(Socket s) throws IOException {
        log.debug("outgoing connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s);
        c.setName(Connection.PARENT);
        connections.add(c);
        Message.authenticate(c);
        return c;
    }

    @Override
    public void run() {
        log.info("using activity interval of " + Settings.getActivityInterval() + " milliseconds");
        while (!term) {
            load = 0;
            for (Connection c : connections) {
                if (!c.getName().equals(Connection.PARENT) && !c.getName().equals(Connection.CHILD)) {
                    load++;
                }
            }

            for (Connection c : connections) {
                if (c.isOpen() && (c.getName().equals(Connection.PARENT) || c.getName().equals(Connection.CHILD))) {
                    Message.serverAnnounce(c, load);
                }
            }

            // do something with 5 second intervals in between
            try {
                Thread.sleep(Settings.getActivityInterval());
            } catch (InterruptedException e) {
                log.info("received an interrupt, system is shutting down");
                break;
            }


        }
        log.info("closing " + connections.size() + " connections");
        // clean up
        for (Connection connection : connections) {
            connection.closeCon();
        }

        listener.setTerm(true);
    }

    public final void setTerm(boolean t) {
        term = t;
    }

    public int getLoad() {
        return load;
    }

    public List<Connection> getConnections() {
        return connections;
    }

    public void addLocalRegisteredUser(String username, String secret) {
        localRegisteredUsers.put(username, new User(username, secret));
    }

    public Map<String, User> getLocalRegisteredUsers() {
        return localRegisteredUsers;
    }

    public void addToBeRegisteredUser(JsonObject request, Connection con) {
        toBeRegisteredUsers.put(request, con);
    }

    public Map<JsonObject, Connection> getToBeRegisteredUsers() {
        return toBeRegisteredUsers;
    }

    public void addExternalRegisteredUser(String username, String secret) {
        externalRegisteredUsers.put(username, new User(username, secret));
    }

    public Map<String, User> getExternalRegisteredUsers() {
        return externalRegisteredUsers;
    }

}
