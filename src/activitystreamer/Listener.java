package activitystreamer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import activitystreamer.utils.Settings;

public class Listener extends Thread {
    
    
    private ServerSocket serverSocket = null;
    private Integer port;
    private boolean flag = false;
    
    public Listener() throws IOException {
        
        port = Settings.getLocalPort();
        
        //listen on port ${port}
        serverSocket = new ServerSocket(port);
        
        start();
    }

    
    @Override
    public void run() {
        
        while (!flag) {
            
            //unknown socket whether from server or client 
            Socket unknown;
            try {
                unknown = serverSocket.accept();
                Control.getInstance().incomingConnection(unknown);
                
            } catch (IOException e) {
                e.printStackTrace();
                flag = true;
            }
            
            
        }
        
    }
    
}
