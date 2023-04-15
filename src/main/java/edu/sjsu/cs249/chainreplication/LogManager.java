package edu.sjsu.cs249.chainreplication;

import org.apache.logging.log4j.Logger;

/**
 * @author ashish
 */
public class LogManager {

    private static final Logger logger = org.apache.logging.log4j.LogManager.getLogger(LogManager.class);

    void log(String log){
        System.out.println("---------------------");
        System.out.println();
        System.out.println("---------------------");
        System.out.println(log);
        System.out.println("---------------------");
    }

    void log(String context,String log){
        System.out.println("---------------------");
        System.out.println(context);
        System.out.println("---------------------");
        System.out.println(log);
        System.out.println("---------------------");
    }
}
