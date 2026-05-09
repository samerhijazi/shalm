package io.shalm;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class AccountService {

    private final Map<String, Integer> balances = new ConcurrentHashMap<>(Map.of(
            "org1", 1000,
            "org2", 1000,
            "org3", 1000
    ));

    public Integer getBalance(String id) {
        return balances.get(id);
    }

    public Set<String> getAccounts() {
        return balances.keySet();
    }

    public synchronized boolean transfer(String from, String to, int amount) {
        Integer fromBal = balances.get(from);
        Integer toBal = balances.get(to);
        if (fromBal == null || toBal == null) return false;
        if (fromBal < amount) return false;
        balances.put(from, fromBal - amount);
        balances.put(to, toBal + amount);
        return true;
    }
}
