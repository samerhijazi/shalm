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
}
