package dev.filebridge.controller;

import dev.filebridge.model.DirectoryListing;
import dev.filebridge.security.AuthorizationService;
import dev.filebridge.security.Permission;
import dev.filebridge.security.UserAccess;
import dev.filebridge.service.FtpFileService;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FileBrowserController {
    private static final Logger log = LoggerFactory.getLogger(FileBrowserController.class);

    private final FtpFileService ftpFileService;
    private final AuthorizationService authorizationService;

    public FileBrowserController(FtpFileService ftpFileService, AuthorizationService authorizationService) {
        this.ftpFileService = ftpFileService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/")
    public String index(
            @RequestParam(required = false) String remotePath,
            Authentication authentication,
            Model model
    ) {
        try {
            UserAccess access = authorizationService.accessFor(authentication);
            String startPath = authorizationService.startPathFor(authentication, remotePath);
            DirectoryListing remote = ftpFileService.list(startPath);
            model.addAttribute("remote", remote);
            model.addAttribute("remoteRoot", access.rootPath());
            model.addAttribute("remoteParentPath", authorizationService.parentPath(authentication, remote.path()));
            model.addAttribute("canUpload", access.role().has(Permission.UPLOAD));
            model.addAttribute("canDownload", access.role().has(Permission.DOWNLOAD));
            model.addAttribute("canDelete", access.role().has(Permission.DELETE));
            model.addAttribute("currentRole", access.role().name());
            model.addAttribute("currentUser", access.username());
        } catch (Exception ex) {
            model.addAttribute("fatalError", userMessage(ex));
        }
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/transfer/upload")
    public String uploadToRemote(
            @RequestParam String remotePath,
            @RequestParam MultipartFile file,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            String normalizedRemotePath = authorizationService.normalizeAuthorizedPath(authentication, Permission.UPLOAD, remotePath);
            ftpFileService.upload(file, normalizedRemotePath);
            log.info("audit upload user={} remotePath={} filename={} size={}",
                    username(authentication),
                    normalizedRemotePath,
                    safeFilename(file),
                    file.getSize());
            redirectAttributes.addFlashAttribute("message", "Datei wurde in den gemeinsamen Speicher hochgeladen.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", userMessage(ex));
        }
        return redirect(remotePath, redirectAttributes);
    }

    @GetMapping("/transfer/download")
    public ResponseEntity<StreamingResponseBody> downloadFromRemote(
            @RequestParam String remoteFile,
            Authentication authentication
    ) {
        try {
            String normalizedRemotePath = authorizationService.normalizeAuthorizedPath(authentication, Permission.DOWNLOAD, remoteFile);
            String fileName = ftpFileService.fileName(normalizedRemotePath);
            StreamingResponseBody body = outputStream -> ftpFileService.streamDownload(normalizedRemotePath, outputStream);
            log.info("audit download user={} remotePath={} filename={}",
                    username(authentication),
                    normalizedRemotePath,
                    fileName);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(fileName, StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .body(body);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, userMessage(ex), ex);
        }
    }

    @PostMapping("/transfer/delete")
    public String deleteRemoteFile(
            @RequestParam String remotePath,
            @RequestParam(defaultValue = "false") boolean directory,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            if (directory) {
                throw new SecurityException("Ordner duerfen ueber die Weboberflaeche nicht geloescht werden.");
            }

            String normalizedRemotePath = authorizationService.normalizeAuthorizedPath(authentication, Permission.DELETE, remotePath);
            String returnPath = authorizationService.parentPath(authentication, normalizedRemotePath);
            ftpFileService.delete(normalizedRemotePath, false);
            log.info("audit delete user={} remotePath={}",
                    username(authentication),
                    normalizedRemotePath);
            redirectAttributes.addFlashAttribute("message", "Datei wurde aus dem gemeinsamen Speicher geloescht.");
            return redirect(returnPath, redirectAttributes);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", userMessage(ex));
            return redirect(remotePath, redirectAttributes);
        }
    }

    private static String redirect(String remotePath, RedirectAttributes redirectAttributes) {
        redirectAttributes.addAttribute("remotePath", remotePath == null ? "/" : remotePath);
        return "redirect:/";
    }

    private static String username(Authentication authentication) {
        return authentication == null ? "anonymous" : authentication.getName();
    }

    private static String safeFilename(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            return "upload.bin";
        }
        return file.getOriginalFilename();
    }

    private static String userMessage(Exception ex) {
        if (ex instanceof SecurityException) {
            return "Zugriff wurde verweigert.";
        }
        String message = ex.getMessage();
            if (message == null || message.isBlank()) {
            return "Die Aktion konnte nicht ausgefuehrt werden.";
        }
        return message;
    }
}
