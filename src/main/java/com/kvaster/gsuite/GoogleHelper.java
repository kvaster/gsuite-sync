package com.kvaster.gsuite;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.admin.directory.Directory;
import com.google.api.services.admin.directory.DirectoryScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

public class GoogleHelper {
    public static Directory createDirectoryService(File credentialsFile, String delegatedUser)
            throws IOException {
        HttpTransport httpTransport = new NetHttpTransport();
        JacksonFactory jsonFactory = new JacksonFactory();

        GoogleCredentials credentials = loadServiceCredentials(credentialsFile, delegatedUser, () -> httpTransport);

        return new Directory.Builder(
                httpTransport,
                jsonFactory,
                new HttpCredentialsAdapter(credentials)
        ).setApplicationName("Master Sync Manager").build();
    }

    public static GoogleCredentials loadServiceCredentials(
            File credentialsFile, String delegatedUser, HttpTransportFactory tf
    ) throws IOException {
        try (InputStream is = new FileInputStream(credentialsFile)) {
            return ServiceAccountCredentials.fromStream(is, tf)
                    .createDelegated(delegatedUser)
                    .createScoped(DirectoryScopes.ADMIN_DIRECTORY_USER);
        }
    }
}
