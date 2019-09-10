package com.kvaster.gsuite;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {
    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        try {
            LOG.info("Starting...");

            File configFile = new File(System.getProperty("config", "config.yml"));

            GSuiteSyncConfig config = ConfigLoader.loadConfig(configFile, GSuiteSyncConfig.class);
            GSuiteSyncService service = new GSuiteSyncService(config);

            Runtime.getRuntime().addShutdownHook(new Thread(service::stopService));
            service.startService();
        } catch (Exception e) {
            LOG.error("error", e);
            System.exit(1);
        }
    }
}
