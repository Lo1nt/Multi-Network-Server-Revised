package activitystreamer.server;

import activitystreamer.util.Settings;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;

public class BroadcastMessage {
    private static final Logger log = LogManager.getLogger();
    private static BroadcastMessage broadcastMessage = null;
    private Control control;
    //    BlockingQueue<String> messageQueue = new LinkedBlockingQueue<String>();
    private static ConcurrentLinkedQueue<JsonObject> messageQueue = new ConcurrentLinkedQueue<JsonObject>();
    Map<JsonObject, List<Connection>> coveredConnections = new ConcurrentHashMap<>();

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
                    //TODO eject message from the queue.
                    JsonObject msg = messageQueue.poll();
                    relayMessage(msg);

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
        List<Connection> connectionList = Collections.synchronizedList(new ArrayList<>());

        connectionList.add(con);
        coveredConnections.put(msg, connectionList);
        messageQueue.offer(msg);
    }

    /**
     * Relay message to all servers it connects EXCEPT source server
     *
     * @param msg
     */
    private void relayMessage(JsonObject msg) {
        List<Connection> connectionList = coveredConnections.get(msg);
        for (Connection c : control.getConnections()) {
            if (!connectionList.contains(c) && (c.getName().equals(Connection.PARENT) || c.getName().equals(Connection.CHILD))) {
                c.writeMsg(msg.toString());
            }
        }
    }
}
