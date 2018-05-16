package activitystreamer;

import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import activitystreamer.utils.Settings;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;

public class Server {
    
    
    
    public static void main(String[] args) {
        
        Options options = new Options();
        options.addOption("lh", true, "local hostname");
        options.addOption("lp", true, "local port");
        options.addOption("rh", true, "remote hostname");
        options.addOption("rp", true, "remote port");
        options.addOption("s", true, "secret for server authenticate");
        
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        
        if (cmd.hasOption("lh")) {
            Settings.setLocalHostname(cmd.getOptionValue("lh"));
        }
        
        if (cmd.hasOption("lp")) {
            try {
                Integer port = Integer.parseInt(cmd.getOptionValue("lp"));
                Settings.setLocalPort(port);
            } catch (NumberFormatException e) {
                System.err.println("-lp calls for a number");
            }
            
        }
        
        if (cmd.hasOption("rh")) {
            Settings.setLocalHostname(cmd.getOptionValue("rh"));
        }
        
        if (cmd.hasOption("rp")) {
            try {
                Integer port = Integer.parseInt(cmd.getOptionValue("rp"));
                Settings.setLocalPort(port);
            } catch (NumberFormatException e) {
                System.err.println("-rp calls for a number");
            }
        }
        
        if (cmd.hasOption("s")) {
            Settings.setServerSecret(cmd.getOptionValue("s"));
            if (!cmd.hasOption("rh") && !cmd.hasOption("rp")) {
                System.out.println("server secret: " + cmd.getOptionValue("s"));
            }
        }
        
        
    }
}
