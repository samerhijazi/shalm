package io.shalm.ui;

public class TestSummary {
    public String app;
    public int tests;
    public int passed;
    public int failures;
    public int errors;
    public int skipped;
    public String timestamp;
    public String commit;

    public boolean isPassing() {
        return failures == 0 && errors == 0;
    }
}
