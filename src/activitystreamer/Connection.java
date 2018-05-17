package activitystreamer;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Connection extends Thread {

    private DataInputStream dis;
    private DataOutputStream dos;
    private BufferedReader br;
    private PrintWriter pw;
    private Socket socket;
    private Integer connID;
    
    private boolean open = false;
    private boolean flag = false;
    
    /**
     * establish connection; establish input and output stream for read and write;
     * @param s
     */
    public Connection(Socket s) {
        try {
            dis = new DataInputStream(s.getInputStream());
            dos = new DataOutputStream(s.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        br = new BufferedReader(new InputStreamReader(dis));
        pw = new PrintWriter(dos, true);
        this.socket = s;
        open = true;
        start();
    }
    
    
    
    @Override
    public void run() {
        try {
            String data;
            while (!flag && (data = br.readLine()) != null) {
                // deal with incomming data
//                flag = Control.getInstance().process(this, data);
            }
            
            
            // close connection
//            Control.getInstance().connectionClosed(this);
            dis.close();
        } catch (IOException e) {
           // if exception, close connection
//            Control.getInstance().connectionClosed(this);
        }
        open = false;
        
    }



    public Integer getConnID() {
        return connID;
    }

    public void setConnID(Integer connID) {
        this.connID = connID;
    }



    
    public boolean writeMsg(String msg) {
        
        if (open) {
            pw.println(msg);
            pw.flush();
            return true;
        }
        return false;
    }

    
    
    
}
