package activitystreamer.server;

import activitystreamer.util.Message;
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
    // this is used to record whether we have got the ack from still existing servers.
//    Map<JsonObject, List<String>> coveredServers = new ConcurrentHashMap<>();
    Map<JsonObject, Connection> linkMsgCon = new ConcurrentHashMap<>();
    // destroy when list has the same values with otherServers before.
    // this is used to record whether we have got ack from all servers, if not, when they reconnect, we sent it again.
    // god.
    Map<JsonObject, List<String>> waitAck = new ConcurrentHashMap<>();
    // work with waitAck
    private Map<JsonObject, List<String>> snapshotOtherServers = new ConcurrentHashMap<>();
    // work with coveredServers
//    public Map<JsonObject, List<String>> remainOtherServers = new ConcurrentHashMap<>();

    //    private List<String> renewServerList =Collections.synchronizedList(new ArrayList<>());
    private BroadcastMessage() {
        control = Control.getInstance();

        new Thread(new Runnable() {

            @Override
            public void run() {
                JsonObject msg;
                while (true) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    msg = messageQueue.poll();
                    if (msg != null) {
                        System.out.println("msg from queue");
                        System.out.println(msg.toString());
                        relayMessage(msg);
                        waitAck.put(msg, Collections.synchronizedList(new ArrayList<>()));
                        if (waitAck.containsKey(msg)) {
                            System.out.println("waitack got the msg");

                        }
                        // simple one, but robust.
                    }
                    for (JsonObject message : snapshotOtherServers.keySet()) {
                        List<String> ackList = waitAck.get(message);
                        List<String> snapList = snapshotOtherServers.get(message);
                        boolean flag = true;
                        for (String serverId : snapList) {
                            if (!ackList.contains(serverId)) {
                                flag = false;
//                                System.out.println("break");
                                break;
                            }
                        }
                        if (!flag) {
//                            System.out.println("resend message");
                            relayMessage(message);
                        } else {
                            System.out.println("success");
                            Message.broadcastSuccess(linkMsgCon.get(message), message);
                            waitAck.remove(message);
                            snapshotOtherServers.remove(message);
                        }
                    }
//                    This one is complex. enhance when got time.
//                    for (JsonObject message : waitAck.keySet()) {
//                        List<String> serverList = snapshotOtherServers.get(msg);
//                        List<String> coveredList = waitAck.get(msg);
//                        boolean flag = true;
//                        for (String serverId : serverList) {
//                            if (!coveredList.contains(serverId)) {
//                                flag = false;
//                                break;
//                            }
//                        }
//                        if (flag == true) {
//                            Message.broadcastSuccess(linkMsgCon.get(msg), msg);
//                            linkMsgCon.remove(msg);
//                            snapshotOtherServers.remove(msg);
//                            waitAck.remove(msg);
//                        } else {
//                            List<String> remainList = remainOtherServers.get(msg);
//
//                            // if connect new servers, flag_reconnect = true.
//                            boolean flag_reconnect = true;
//                            // TODO if the crashed servers  reconnect, no matter how many, send the msg.
//                            // TODO when server in snapshotOtherServers, in otherServers but not in remainOtherServers,
//                            // TODO means this server is reconnected, then add it to remainOtherServer.
//                            // For now, only take one situation into consideration, which is when all crashed servers r back,
//                            // then we send the msg.
//                            for (String serverId : serverList) {
//                                // if one in snapshot, not in remain, flag = false
//                                flag = true;
//                                if (!remainList.contains(serverId)) {
//                                    flag = false;
//                                }
//                                // if one in snapshot, not in otherServer, flag = True. means it isn't reconnected yet.
//                                flag_reconnect = true;
//                                if (!Control.getInstance().getOtherServers().containsKey(serverId)) {
//                                    // may break.
//                                    flag_reconnect = false;
//                                }
//                                if (!flag && flag_reconnect) {
//                                    relayMessage(message);
//                                }
//                            }
//                            // some severs crashed. and all reconnect. send the msg again to these servers.
//                            if (!flag && flag_reconnect) {
//                                // broadcast again.
//                                // TODO those who have received once, they don't have to receive again.
//                                // TODO do this by adding id.
//                            }
//                        }
//                    }
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
//        coveredServers.put(msg, Collections.synchronizedList(new ArrayList<>()));
        System.out.println(msg.toString());
        linkMsgCon.put(msg, con);
        messageQueue.offer(msg);
        List<String> tmp = new ArrayList<>(Control.getInstance().getOtherServers().keySet());
        // may use deep copy.
        snapshotOtherServers.put(msg, tmp);
//        remainOtherServers.put(msg, tmp);
    }

    public boolean checkAck(JsonObject request) {
        JsonObject msg = (JsonObject) request.get("msg");
        String timestamp = msg.get("time").getAsString();
        System.out.println(msg.toString());
        String serverId = request.get("from").getAsString();
        System.out.println("check ack");
        for (JsonObject message : waitAck.keySet()) {
//            System.out.println(message.toString());
            String waitTime = message.get("time").getAsString();
            if (waitTime.equals(timestamp)) {
                waitAck.get(message).add(serverId);
                return true;
            }
        }

//        if (waitAck.containsKey(msg)) {
//            System.out.println("have that msg");
//
//            if (waitAck.get(msg).contains(serverId)) {
//                waitAck.get(msg).add(serverId);
//                System.out.println("add ack");
//            }
//            return true;
//        }
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
