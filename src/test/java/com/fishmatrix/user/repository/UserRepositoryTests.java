package com.fishmatrix.user.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fishmatrix.user.model.UserAccount;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.cloud.nacos.discovery.enabled=false"})
class UserRepositoryTests {

    @Autowired
    private UserRepository users;

    @Test
    void searchesByUsernameAndApplicationId() {
        long id = users.insert("repo_probe", "hash", "repo_probe", "USER", "PENDING",
                LocalDateTime.now());

        List<UserAccount> byName = users.search(null, "repo_probe", 10, 0);
        List<UserAccount> byHash = users.search(null, "#" + id, 10, 0);
        List<UserAccount> byPlainId = users.search(null, Long.toString(id), 10, 0);

        assertTrue(byName.stream().anyMatch(user -> user.id() == id));
        assertTrue(byHash.stream().anyMatch(user -> user.id() == id));
        assertTrue(byPlainId.stream().anyMatch(user -> user.id() == id));
    }
}
