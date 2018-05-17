package activitystreamer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import activitystreamer.utils.Settings;

public class Listener implements Runnable {
    
    
    private ServerSocket serverSocket = null;
    private Integer port;
    private boolean flag = false;
    
    public Listener() {
        
        port = Settings.getLocalPort();
        
        //listen on port ${port}
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        start();
    }

    
    @Override
    public void run() {
        
        while (!flag) {
            Socket unknown;
            try {
                unknown = serverSocket.accept();
                Control.getInstance();
                
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            
        }
        
    }
    
}
