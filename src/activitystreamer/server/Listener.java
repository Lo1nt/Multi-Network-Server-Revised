package activitystreamer.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import activitystreamer.util.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Listener extends Thread {

    private static final Logger log = LogManager.getLogger();
    private ServerSocket serverSocket = null;
    private Integer port;
    private boolean term = false;

    public Listener() throws IOException {
        port = Settings.getLocalPort(); // keep our own copy in case it changes later
        serverSocket = new ServerSocket(port);
        start();
    }


    @Override
    public void run() {
        log.info("listening for new connections on " + port);
        while (!term) {
            Socket clientSocket;
            try {
                clientSocket = serverSocket.accept();
                Control.getInstance().incomingConnection(clientSocket);
            } catch (IOException e) {
                log.info("received exception, shutting down");
                term = true;
            }
        }
    }

    public void setTerm(boolean term) {
        this.term = term;
        if (term) {
            interrupt();
        }
    }

}
