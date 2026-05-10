package io.shalm;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class AccountService {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        seed("ACC-B1-001", "ClientA", "Bank1", 1000);
        seed("ACC-B1-002", "ClientC", "Bank1",  500);
        seed("ACC-B2-001", "ClientB", "Bank2", 1000);
        seed("ACC-B2-002", "ClientD", "Bank2",  500);
    }

    private void seed(String id, String owner, String bank, int balance) {
        accounts.put(id, new Account(id, owner, bank, balance));
    }

    public Account getAccount(String id) {
        return accounts.get(id);
    }

    public Collection<Account> getAllAccounts() {
        return accounts.values().stream()
                .sorted(Comparator.comparing(a -> a.id))
                .collect(Collectors.toList());
    }

    public synchronized boolean transfer(String fromId, String toId, int amount) {
        Account from = accounts.get(fromId);
        Account to   = accounts.get(toId);
        if (from == null || to == null) return false;
        if (from.balance < amount) return false;
        from.balance -= amount;
        to.balance   += amount;
        return true;
    }
}
