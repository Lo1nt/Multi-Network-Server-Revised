package activitystreamer.server;

import activitystreamer.util.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class BroadcastMessage {
    private static final Logger log = LogManager.getLogger();
    private Control control;
    

    private BroadcastMessage() {
        control = Control.getInstance();
        new Thread(new Runnable() {

            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    //TODO eject message from the queue.
                }
            }

        }).start();
    }
}
