package version4;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;


public class LogIt {

    private static Logger logger;

    private LogIt() throws IOException {
        //instance the logger
        logger = Logger.getLogger(LogIt.class.getName());
        //instance the filehandler
        //instance formatter, set formatting, and handler
    }

    private static Logger getLogger(){
        if (logger == null){
            try{
                new LogIt();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return logger;
    }

    public static void log(Level level, String msg){
        getLogger().log(level, msg);
        //System.out.println(msg);
    }

}
