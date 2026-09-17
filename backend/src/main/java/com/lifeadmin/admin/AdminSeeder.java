package com.lifeadmin.admin;

import com.lifeadmin.account.Account;
import com.lifeadmin.account.AccountRepository;
import com.lifeadmin.account.AppUser;
import com.lifeadmin.account.AppUserRepository;
import com.lifeadmin.account.Plan;
import com.lifeadmin.account.Subscription;
import com.lifeadmin.account.SubscriptionRepository;
import com.lifeadmin.account.UserRole;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a single platform administrator ({@link UserRole#SUPER_ADMIN}) at startup if one does not
 * already exist for the configured email. Idempotent: safe to run on every boot. Credentials come
 * from {@code lifeadmin.admin.*} (env-overridable); the password is hashed with the app's
 * {@link PasswordEncoder}, never stored in plaintext or in a migration.
 */
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final AccountRepository accountRepository;
    private final AppUserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${lifeadmin.admin.email:admin@lifeadmin.local}")
    private String adminEmail;
    @Value("${lifeadmin.admin.password:ChangeMeAdmin123!}")
    private String adminPassword;
    @Value("${lifeadmin.admin.name:Platform Admin}")
    private String adminName;

    @Override
    @Transactional
    public void run(final ApplicationArguments args) {
        if (userRepository.existsByEmailIgnoreCase(adminEmail)) {
            return;
        }
        final var account = new Account();
        account.setName("Platform Administration");
        final var savedAccount = accountRepository.save(account);

        final var admin = new AppUser();
        admin.setAccountId(savedAccount.getId());
        admin.setEmail(adminEmail.toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setName(adminName);
        admin.setRole(UserRole.SUPER_ADMIN);
        userRepository.save(admin);

        // Give the admin account a subscription row too, so /auth/me and quota logic are uniform.
        final var subscription = new Subscription();
        subscription.setAccountId(savedAccount.getId());
        subscription.setPlan(Plan.FREE);
        subscriptionRepository.save(subscription);

        log.info("Seeded platform admin user '{}' (change the password via lifeadmin.admin.password)", adminEmail);
    }
}
