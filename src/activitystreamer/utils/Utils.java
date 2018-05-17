package activitystreamer.utils;

import org.json.simple.JSONObject;

import activitystreamer.Connection;

@SuppressWarnings("unchecked")
public class Utils {
    
    
    
    

    /**
     * send authenticate info
     * @param con
     */
    public synchronized static void authenticate(Connection con) {
        JSONObject json = new JSONObject();
        json.put("command", Message.AUTHENTICATE);
        json.put("secret", Settings.getServerSecret());
        con.writeMsg(json.toJSONString());
    }
}
