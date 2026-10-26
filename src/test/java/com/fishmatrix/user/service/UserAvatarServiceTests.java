package com.fishmatrix.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.common.storage.LocalFileStorage;
import com.fishmatrix.user.model.UserAccount;
import com.fishmatrix.user.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class UserAvatarServiceTests {

    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};

    @TempDir
    Path tempDir;

    @Mock
    private UserRepository users;

    private LocalFileStorage storage;
    private UserAvatarService service;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage(tempDir.toString(), 1024 * 1024);
        service = new UserAvatarService(storage, users, 1024 * 1024);
    }

    @Test
    void uploadStoresImageAndUpdatesAccount() {
        UserAccount account = account(null);
        byte[] png = with(0x42);
        MultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", png);
        UserAccount updated = account("avatars/1/avatar.png");
        when(users.findById(1L)).thenReturn(Optional.of(updated));

        UserAccount result = service.upload(account, file);

        verify(users).updateAvatar(eq(1L), anyString(), eq("image/png"), any(LocalDateTime.class));
        assertEquals("avatars/1/avatar.png", result.avatarKey());
        assertTrue(result.avatarUrl().startsWith("/api/v1/user/avatars/1?v="));
    }

    @Test
    void uploadReplacesPreviousFile() throws IOException {
        Path oldFile = tempDir.resolve("avatars/1/old.png");
        Files.createDirectories(oldFile.getParent());
        Files.write(oldFile, PNG);
        UserAccount account = account("avatars/1/old.png");
        MultipartFile file = new MockMultipartFile("file", "new.png", "image/png", with(0x42));
        when(users.findById(1L)).thenReturn(Optional.of(account("avatars/1/new.png")));

        service.upload(account, file);

        assertFalse(Files.exists(oldFile));
    }

    @Test
    void uploadRejectsNonImage() {
        MultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain",
                "not an image".getBytes());
        ApiException error = assertThrows(ApiException.class,
                () -> service.upload(account(null), file));

        assertEquals(HttpStatus.BAD_REQUEST, error.status());
    }

    @Test
    void uploadRejectsOversizedFile() {
        UserAvatarService small = new UserAvatarService(storage, users, 4);
        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", with(0x42));

        ApiException error = assertThrows(ApiException.class,
                () -> small.upload(account(null), file));

        assertEquals(HttpStatus.BAD_REQUEST, error.status());
    }

    @Test
    void loadMissingAvatarIsNotFound() {
        when(users.findById(9L)).thenReturn(Optional.of(account(9L, null)));

        ApiException error = assertThrows(ApiException.class, () -> service.load(9L));

        assertEquals(HttpStatus.NOT_FOUND, error.status());
    }

    @Test
    void clearRemovesFileAndResetsAccount() throws IOException {
        Path oldFile = tempDir.resolve("avatars/1/old.png");
        Files.createDirectories(oldFile.getParent());
        Files.write(oldFile, PNG);
        UserAccount account = account("avatars/1/old.png");
        when(users.findById(1L)).thenReturn(Optional.of(account(1L, null)));

        UserAccount result = service.clear(account);

        verify(users).updateAvatar(eq(1L), isNull(), isNull(), any(LocalDateTime.class));
        assertFalse(Files.exists(oldFile));
        assertNull(result.avatarUrl());
    }

    private UserAccount account(String avatarKey) {
        return account(1L, avatarKey);
    }

    private UserAccount account(long id, String avatarKey) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 26, 12, 0);
        return new UserAccount(id, "u" + id, "hash", "良一情", avatarKey,
                avatarKey == null ? null : "image/png", avatarKey == null ? null : now,
                "USER", "ACTIVE", now, now, null, null, null);
    }

    private static byte[] with(int marker) {
        byte[] bytes = PNG.clone();
        bytes[bytes.length - 1] = (byte) marker;
        return bytes;
    }
}
