package activitystreamer.server;

import activitystreamer.util.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;

public class Connection extends Thread implements Serializable {

    private static final Logger log = LogManager.getLogger();
    private DataInputStream dis;
    private DataOutputStream dos;
    private BufferedReader br;
    private PrintWriter pw;
    private boolean open = false;
    private Socket socket;
    private boolean term = false;

    private Integer connID;
    private long connTime;
    private boolean isAuthenticated = false;
    private boolean isLoggedIn = false;

    public static final String PARENT = "PARENT";
    public static final String CHILD = "CHILD";

    public Connection(Socket s) throws IOException {
        dis = new DataInputStream(s.getInputStream());
        dos = new DataOutputStream(s.getOutputStream());
        br = new BufferedReader(new InputStreamReader(dis));
        pw = new PrintWriter(dos, true);
        this.socket = s;
        open = true;
        start();
    }

    public boolean writeMsg(String msg) {
        if (open) {
            pw.println(msg);
            pw.flush();
            return true;
        }
        return false;
    }

    public void closeCon() {
        if (open) {
            log.info("closing connection by closeCon " + Settings.socketAddress(socket));
            try {
                term = true;
                br.close();
                dos.close();
            } catch (IOException e) {
                // already closed?
                log.error("received exception closing the connection " + Settings.socketAddress(socket) + ": " + e);
            }
        }
    }

    @Override
    public void run() {
        try {
            String data;
            while (!term && (data = br.readLine()) != null) {
                term = Control.getInstance().process(this, data);
            }
            log.debug("connection closed to " + Settings.socketAddress(socket));
            Control.getInstance().connectionClosed(this);
            dis.close();
        } catch (IOException e) {
            log.error("connection " + Settings.socketAddress(socket) + " closed with exception: " + e);
            Control.getInstance().connectionClosed(this);
        }
        open = false;
    }

    public Socket getSocket() {
        return socket;
    }

    public boolean isOpen() {
        return open;
    }

    public Integer getConnID() {
        return connID;
    }

    public void setConnID(Integer connID) {
        this.connID = connID;
    }

    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        isLoggedIn = loggedIn;
    }

    public long getConnTime() {
        return connTime;
    }

    public void setConnTime(long connTime) {
        this.connTime = connTime;
    }

    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        isAuthenticated = authenticated;
    }


}
