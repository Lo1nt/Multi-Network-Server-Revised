package activitystreamer.client;

import activitystreamer.util.Message;
import activitystreamer.util.Settings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;

public class ClientSkeleton extends Thread {

    private static final Logger log = LogManager.getLogger();
    private static ClientSkeleton clientSolution;
    private TextFrame textFrame;
    private Socket socket;
    private DataOutputStream dos;
    private PrintWriter out;
    private DataInputStream dis;
    private InputStreamReader isr;
    private BufferedReader br;
    private JsonParser jp;

    public static ClientSkeleton getInstance() {
        if (clientSolution == null) {
            clientSolution = new ClientSkeleton();
        }
        return clientSolution;
    }

    public ClientSkeleton() {
        try {
            socket = new Socket(Settings.getRemoteHostname(), Settings.getRemotePort());
            System.out.println("remote hostname and port is:");
            System.out.println(Settings.getRemoteHostname() + " " + Settings.getRemotePort());
            jp = new JsonParser();
            dos = new DataOutputStream(socket.getOutputStream());
            out = new PrintWriter(dos, true);
            dis = new DataInputStream(socket.getInputStream());
            isr = new InputStreamReader(dis);
            br = new BufferedReader(isr);
        } catch (IOException e) {
            e.printStackTrace();
        }
        textFrame = new TextFrame();
        start();
    }

    @SuppressWarnings("unchecked")
    public void sendActivityObject(JsonObject activityObj) {
        if (activityObj.has("activity")) {
            JsonObject jo = new JsonObject();
            jo.addProperty("command", Message.ACTIVITY_MESSAGE);
            jo.addProperty("username", Settings.getUsername());
            jo.addProperty("secret", Settings.getUserSecret());
            jo.add("activity", activityObj.get("activity"));
            //	{"activity":{"S":"S"}}
            activityObj = jo;
        }
        out.write(activityObj.toString() + "\n");
        out.flush();
    }

    public void disconnect() {
        textFrame.dispose();
        System.exit(0);
    }

    public void run() {
        String msg;
        try {
            initMsg();
            while (true) {
                if (socket.isClosed()) {
                    break;
                }
                msg = br.readLine();
                System.out.println(msg);
                if (msg == null) {
                    break;
                }
                process(msg);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * send initial message to server: LOGIN or REGISTER when socket established
     *
     * @throws IOException
     */
    private void initMsg() {
        //dos.wirteUTF fail to write \n as normal
        if (Settings.getUserSecret() != null) {
            // login
            out.write(Message.login(Settings.getUsername()) + "\n");
            out.flush();
        } else if (Settings.getUsername().equals("anonymous")) {
            // login as anonymous
            out.write(Message.login() + "\n");
            out.flush();
        } else {
            // register
            Settings.setUserSecret(Settings.genRandomString());
            System.out.println("ur secret is: " + Settings.getUserSecret());
            out.write(Message.register(Settings.getUsername(), Settings.getUserSecret()) + "\n");
            out.flush();
        }

    }


    /**
     * process incoming message
     *
     * @param msg
     * @return JsonObject
     * @throws IOException
     */
    private void process(String msg) throws IOException {
        JsonObject jo = (JsonObject) jp.parse(msg);
        textFrame.setOutputText(jo);
        String cmd = jo.get("command").getAsString();
        switch (cmd) {
            case Message.REGISTER_SUCCESS:
                out.write(Message.login(Settings.getUsername()) + "\n");
                out.flush();
                break;

            case Message.REDIRECT:
                redirect(jo);
                break;
            case Message.REGISTER_FAILED:
            case Message.INVALID_MESSAGE:
                if (!socket.isClosed()) {
                    socket.close();
                }
                textFrame.setOutputText(Message.connCloseMsg());
                break;
        }
    }

    /**
     * deal with REDIRECT message received
     *
     * @param jo
     * @throws IOException
     */
    private void redirect(JsonObject jo) throws IOException {
        String hostname = jo.get("hostname").getAsString();
        int port = ((Long) jo.get("port").getAsLong()).intValue();
        if (!socket.isClosed()) {
            socket.close();
        }
        Settings.setRemoteHostname(hostname);
        Settings.setRemotePort(port);

        try {
            textFrame.setOutputText(Message.redirectMsg());
            socket = new Socket(Settings.getRemoteHostname(), Settings.getRemotePort());
            dos = new DataOutputStream(socket.getOutputStream());
            out = new PrintWriter(dos, true);
            isr = new InputStreamReader(socket.getInputStream());
            br = new BufferedReader(isr);
            initMsg();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
