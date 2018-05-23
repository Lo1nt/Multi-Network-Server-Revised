package activitystreamer.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.simple.JSONObject;

import activitystreamer.server.Connection;

public class Constant {
    
    public static Integer serverID = 10000;
    public static Integer clientID = 10001;

    public static Map<String, ConcurrentLinkedQueue<JSONObject>> messageQueue = new HashMap<String, ConcurrentLinkedQueue<JSONObject>>();
    
}
