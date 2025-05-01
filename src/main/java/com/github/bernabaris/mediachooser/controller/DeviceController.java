package com.github.bernabaris.mediachooser.controller;

import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class DeviceController {

    @Value("${device.base-url}")
    private String deviceBaseUrl;

    @Value("${device.username}")
    private String username;

    @Value("${device.password}")
    private String password;

    @GetMapping("/folders")
    public ResponseEntity<List<String>> listFolders() {
        SMBClient client = new SMBClient();

        try (Connection connection = client.connect(deviceBaseUrl)) {
            AuthenticationContext ac = new AuthenticationContext(username, password.toCharArray(), "");
            Session session = connection.authenticate(ac);

            try (DiskShare share = (DiskShare) session.connectShare("media")) {
                List<String> folders = share.list("").stream()
                        .filter(file -> (file.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0)
                        .filter(file -> !file.getFileName().equals(".") && !file.getFileName().equals(".."))
                        .map(FileIdBothDirectoryInformation::getFileName)
                        .collect(Collectors.toList());

                return ResponseEntity.ok(folders);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of("Error: " + e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(List.of("Error: " + e.getMessage()));
        }
    }
}
