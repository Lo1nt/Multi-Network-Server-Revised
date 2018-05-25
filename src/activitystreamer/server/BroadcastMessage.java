package activitystreamer.server;

import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;

public class BroadcastMessage {
    private static final Logger log = LogManager.getLogger();
    private static BroadcastMessage broadcastMessage = null;
    private Control control;
    //    BlockingQueue<String> messageQueue = new LinkedBlockingQueue<String>();
    private static ConcurrentLinkedQueue<JsonObject> messageQueue = new ConcurrentLinkedQueue<JsonObject>();
    // destroy when List has the same values with otherServers now.
    Map<JsonObject, List<String>> coveredServers = new ConcurrentHashMap<>();
    Map<JsonObject, Connection> linkMsgCon = new ConcurrentHashMap<>();
    // destroy when list has the same values with otherServers before.
    Map<JsonObject, List<String>> waitAck = new ConcurrentHashMap<>();
    private Map<JsonObject, List<String>> snapshotOtherServers = new ConcurrentHashMap<>();
    public Map<JsonObject, List<String>> remainOtherServers = new ConcurrentHashMap<>();

    private BroadcastMessage() {
        control = Control.getInstance();
        new Thread(new Runnable() {

            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    JsonObject msg = messageQueue.poll();
                    relayMessage(msg);
                    waitAck.put(msg, Collections.synchronizedList(new ArrayList<>()));


                    for (JsonObject message : coveredServers.keySet()) {
                        List<String> serverList = coveredServers.get(message);

                    }

                    for (JsonObject message : waitAck.keySet()) {
                        List<String> serverList = waitAck.get(message);

                    }
                }
            }

        }).start();
    }

    public static BroadcastMessage getInstance() {
        if (broadcastMessage == null) {
            broadcastMessage = new BroadcastMessage();
        }
        return broadcastMessage;
    }

    public void injectMsg(Connection con, JsonObject msg) {
        coveredServers.put(msg, Collections.synchronizedList(new ArrayList<>()));
        linkMsgCon.put(msg, con);
        messageQueue.offer(msg);
        List<String> tmp = new ArrayList<>(Control.getInstance().getOtherServers().keySet());
        snapshotOtherServers.put(msg, tmp);
        remainOtherServers.put(msg, tmp);
    }

    public boolean checkAck(JsonObject request) {
        JsonObject msg = (JsonObject) request.get("msg");
        String serverId = request.get("from").getAsString();
        if (coveredServers.containsKey(msg)) {
            coveredServers.get(msg).add(serverId);
            return true;
        }
        return false;
    }

    /**
     * Relay message to all servers it connects EXCEPT source server
     *
     * @param msg
     */
    private void relayMessage(JsonObject msg) {
        Connection from = linkMsgCon.get(msg);
        for (Connection c : control.getConnections()) {
            if (!c.equals(from) && (c.getName().equals(Connection.PARENT) || c.getName().equals(Connection.CHILD))) {
                c.writeMsg(msg.toString());
            }
        }
    }
}
