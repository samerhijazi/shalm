package io.shalm.ui;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@ApplicationScoped
public class TransactionStore {

    private final LinkedList<TransactionRecord> history = new LinkedList<>();
    private static final int MAX = 20;

    public synchronized void add(TransactionRecord record) {
        history.addFirst(record);
        if (history.size() > MAX) history.removeLast();
    }

    public synchronized List<TransactionRecord> getAll() {
        return new ArrayList<>(history);
    }

    public synchronized List<TransactionRecord> getRecent(int n) {
        List<TransactionRecord> all = getAll();
        return all.subList(0, Math.min(n, all.size()));
    }

    public synchronized List<TransactionRecord> getForAccount(String accountId) {
        List<TransactionRecord> result = new ArrayList<>();
        for (TransactionRecord tx : history) {
            if (tx.fromId.equals(accountId) || tx.toId.equals(accountId)) {
                result.add(tx);
            }
        }
        return result;
    }
}
