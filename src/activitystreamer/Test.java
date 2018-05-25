package activitystreamer;

import java.io.IOException;

public class Test {
    public static void main(String[] args) {
//        Server.main(new String[]{"-lh", "localhost", "-lp", "3779", "-s", "123"});
        try {
            Runtime run = Runtime.getRuntime();
            run.exec("java activitystreamer.Server -lh localhost -lp 3780 -s 123");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
