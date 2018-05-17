package activitystreamer;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import activitystreamer.utils.Constant;
import activitystreamer.utils.Settings;

public class Control implements Runnable {

    private static Control control = null; 
    private static Connection parentNode, leftNode, rightNode;
    private static List<Connection> clientConns = null;
    
    
    public static Control getInstance() {
        if (control == null) {
            control =new Control();
        }
        return control;
    }
    
    public Control() {
        clientConns = new ArrayList<Connection>();
        
    }
    
    
    @Override
    public void run() {
        // TODO Auto-generated method stub
        
    }
    
    
    public synchronized Connection incomingConnection(Socket s) throws IOException {
        
        Connection c = new Connection(s);
        c.setConnID(Constant.clientID);
        Constant.clientID += 2;
        clientConns.add(c);
        return c;
    }
    
    
    public synchronized Connection outgoingConnection(Socket s) throws IOException {
        return null;

    }
    
    private boolean authenticate(Connection con, JSONObject request) {
        if (request.get("secret").equals(Settings.getServerSecret())) {
            con.setConnID(Constant.serverID);
            Constant.serverID += 2;
            Constant.clientID -= 2;
            if (leftNode == null) {
                leftNode = con;
            }
            else if (rightNode == null) {
                rightNode = con;
            }
            else {
                // Redirect
            }
        }
        
        
        return false;
    }
    
    
    
    public synchronized boolean process(Connection con, String msg) {
        JSONObject request = null;
        try {
            request = (JSONObject) new JSONParser().parse(msg);
        } catch (Exception e) {
//            return Message.invalidMsg(con, "the received message is not in valid format");
        }

        if (request.get("command") == null) {
//            return Message.invalidMsg(con, "the received message did not contain a command");
        }

        String command = (String) request.get("command");
        
        switch (command) {
            case "AUTHENTICATE":
                return authenticate(con, request);
        }
        return false;
        
    }
    

    
    
}
