package com.github.bernabaris.mediachooser.controller;

import com.github.bernabaris.mediachooser.model.Category;
import com.github.bernabaris.mediachooser.model.MainCategory;
import com.github.bernabaris.mediachooser.model.Media;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.stylesheets.MediaList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class MediaController {

    private static final List<String> MAIN_CATEGORY_NAMES = List.of("Movies","TV Shows");

    @Value("${device.base-url}")
    private String deviceBaseUrl;

    @Value("${device.username}")
    private String username;

    @Value("${device.password}")
    private String password;

    @GetMapping("/folders")
    public ResponseEntity<List<MainCategory>> listFolders() {
        SMBClient client = new SMBClient();

        try (Connection connection = client.connect(deviceBaseUrl)) {
            AuthenticationContext ac = new AuthenticationContext(username, password.toCharArray(), "");
            Session session = connection.authenticate(ac);

            try (DiskShare share = (DiskShare) session.connectShare("media")) {
                List<MainCategory> mainCategories = new ArrayList<>();
                for(String mainCategoryName : MAIN_CATEGORY_NAMES){
                    MainCategory mainCategory = new MainCategory();
                    mainCategory.setName(mainCategoryName);
                    List<String> categoryNames = getFoldersInFolder(share, mainCategoryName);
                    List<Category> categoryList = new ArrayList<>();
                    for(String categoryName:categoryNames) {
                        Category category = new Category();
                        category.setName(categoryName);
                        List<Media> mediaList = getMediaListInCategory(share, String.format("%s/%s",mainCategoryName,categoryName));
                        category.setMediaList(mediaList);
                        categoryList.add(category);
                    }
                    mainCategory.setCategoryList(categoryList);
                    mainCategories.add(mainCategory);
                }

                return ResponseEntity.ok(mainCategories);
            }
        } catch (Exception e) {
            log.error("Error while listing folders: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ArrayList<>());
        }
    }

    private List<String> getFoldersInFolder(DiskShare share, String parentFolderName){
        List<String> folders = new ArrayList<>();
        share.list(parentFolderName).stream()
                .filter(file -> (file.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0)
                .filter(file -> !file.getFileName().equals(".") && !file.getFileName().equals(".."))
                .forEach(fileIdBothDirectoryInformation -> {
            folders.add(fileIdBothDirectoryInformation.getFileName());
        });
        return folders;
    }

    private List<Media> getMediaListInCategory(DiskShare share, String parentFolderName) {
        List<Media> mediaList = new ArrayList<>();

        share.list(parentFolderName).stream()
                .filter(file -> (file.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0)
                .filter(file -> !file.getFileName().equals(".") && !file.getFileName().equals(".."))
                .forEach(directory -> {
                    String subFolderPath = parentFolderName + "\\" + directory.getFileName();
                    long totalBytes = share.list(subFolderPath).stream()
                            .filter(file -> (file.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) == 0)
                            .mapToLong(FileIdBothDirectoryInformation::getEndOfFile)
                            .sum();
                    double sizeInMB = totalBytes / (1024.0 * 1024.0);

                    Media media = new Media();
                    media.setName(directory.getFileName());
                    media.setSize(sizeInMB);
                    mediaList.add(media);
                });

        return mediaList;
    }


}
