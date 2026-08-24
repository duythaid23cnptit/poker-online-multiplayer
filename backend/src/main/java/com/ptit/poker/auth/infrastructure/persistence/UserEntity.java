package com.ptit.poker.auth.infrastructure.persistence;

import com.ptit.poker.auth.domain.AccountStatus;
import com.ptit.poker.auth.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 255, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus;

    @Column(name = "account_chips", nullable = false)
    private long accountChips;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected UserEntity() {
    }

    public UserEntity(String username, String passwordHash, String email, Role role,
                      AccountStatus accountStatus, long accountChips) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.role = role;
        this.accountStatus = accountStatus;
        this.accountChips = accountChips;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public long getAccountChips() {
        return accountChips;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void recordLogin(Instant loginAt) {
        this.lastLoginAt = loginAt;
    }

    public void debitAccountChips(long amount) {
        if (amount <= 0 || accountChips < amount) {
            throw new IllegalArgumentException("Insufficient account chips");
        }
        accountChips -= amount;
    }

    public void creditAccountChips(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Chip amount cannot be negative");
        }
        accountChips = Math.addExact(accountChips, amount);
    }
}
